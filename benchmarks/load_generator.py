"""
Load Generator for Benchmark Comparison
Submits concurrent tasks to both OpenTron (Java) and Python baseline
"""

import asyncio
import aiohttp
import time
import json
import statistics
from dataclasses import dataclass, asdict
from typing import List, Dict, Any
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class BenchmarkResult:
    platform: str  # "opentron" or "python"
    concurrency: int
    total_tasks: int
    successful_tasks: int
    failed_tasks: int
    total_time_seconds: float
    throughput_tasks_per_second: float
    latencies_ms: List[float]
    
    @property
    def p50_latency(self) -> float:
        if not self.latencies_ms:
            return 0
        sorted_latencies = sorted(self.latencies_ms)
        return sorted_latencies[int(len(sorted_latencies) * 0.50)]
    
    @property
    def p95_latency(self) -> float:
        if not self.latencies_ms:
            return 0
        sorted_latencies = sorted(self.latencies_ms)
        return sorted_latencies[int(len(sorted_latencies) * 0.95)]
    
    @property
    def p99_latency(self) -> float:
        if not self.latencies_ms:
            return 0
        sorted_latencies = sorted(self.latencies_ms)
        return sorted_latencies[int(len(sorted_latencies) * 0.99)]
    
    @property
    def max_latency(self) -> float:
        return max(self.latencies_ms) if self.latencies_ms else 0
    
    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["p50_latency_ms"] = self.p50_latency
        d["p95_latency_ms"] = self.p95_latency
        d["p99_latency_ms"] = self.p99_latency
        d["max_latency_ms"] = self.max_latency
        del d["latencies_ms"]  # Don't include raw list in output
        return d


class BenchmarkRunner:
    def __init__(self, base_url: str, platform: str):
        self.base_url = base_url
        self.platform = platform
    
    async def run_benchmark(
        self,
        concurrency: int,
        total_tasks: int,
        task_delay_ms: int = 500
    ) -> BenchmarkResult:
        """
        Run concurrent load test.
        
        Args:
            concurrency: Number of simultaneous requests
            total_tasks: Total number of tasks to submit
            task_delay_ms: Simulated LLM query delay (ms)
        """
        logger.info(f"Starting {self.platform} benchmark: concurrency={concurrency}, total_tasks={total_tasks}")
        
        latencies = []
        successful = 0
        failed = 0
        
        async with aiohttp.ClientSession() as session:
            start_time = time.time()
            
            # Batch requests by concurrency limit
            for batch_start in range(0, total_tasks, concurrency):
                batch_end = min(batch_start + concurrency, total_tasks)
                batch_size = batch_end - batch_start
                
                tasks = [
                    self._submit_query(session, task_delay_ms)
                    for _ in range(batch_size)
                ]
                
                results = await asyncio.gather(*tasks, return_exceptions=True)
                
                for result in results:
                    if isinstance(result, Exception):
                        logger.error(f"Task failed: {result}")
                        failed += 1
                    elif result.get("status") == "error":
                        failed += 1
                    else:
                        successful += 1
                        if "latency_ms" in result:
                            latencies.append(result["latency_ms"])
            
            total_time = time.time() - start_time
        
        throughput = total_tasks / total_time if total_time > 0 else 0
        
        result = BenchmarkResult(
            platform=self.platform,
            concurrency=concurrency,
            total_tasks=total_tasks,
            successful_tasks=successful,
            failed_tasks=failed,
            total_time_seconds=total_time,
            throughput_tasks_per_second=throughput,
            latencies_ms=latencies,
        )
        
        logger.info(f"Benchmark complete: {result.successful_tasks} succeeded, "
                   f"{result.failed_tasks} failed in {total_time:.2f}s "
                   f"({throughput:.2f} tasks/sec)")
        
        return result
    
    async def _submit_query(self, session: aiohttp.ClientSession, delay_ms: int) -> Dict[str, Any]:
        """Submit single query and measure latency"""
        endpoint = f"{self.base_url}/v1/agents/query/blocking"
        
        try:
            start = time.time()
            async with session.post(
                endpoint,
                params={"prompt": "test query", "delay_ms": delay_ms},
                timeout=aiohttp.ClientTimeout(total=60)
            ) as resp:
                data = await resp.json()
                latency_ms = (time.time() - start) * 1000
                data["latency_ms"] = latency_ms
                return data
        except Exception as e:
            logger.error(f"Query failed: {e}")
            return {"status": "error", "error": str(e)}


async def run_comparison_suite():
    """Run complete benchmark suite comparing OpenTron vs Python"""
    
    results = []
    
    # Test configurations
    configs = [
        {"concurrency": 10, "total_tasks": 100},
        {"concurrency": 50, "total_tasks": 500},
        {"concurrency": 100, "total_tasks": 1000},
        {"concurrency": 500, "total_tasks": 2500},
        {"concurrency": 1000, "total_tasks": 5000},
    ]
    
    # Benchmark Java OpenTron
    logger.info("=" * 60)
    logger.info("BENCHMARKING OPENTRON (Java 21 + Virtual Threads)")
    logger.info("=" * 60)
    
    java_runner = BenchmarkRunner("http://opentron-backend:8080", "opentron-java")
    
    for config in configs:
        try:
            result = await java_runner.run_benchmark(**config, task_delay_ms=500)
            results.append(result)
            logger.info(f"Result: {json.dumps(result.to_dict(), indent=2)}")
        except Exception as e:
            logger.error(f"Java benchmark failed for config {config}: {e}")
    
    # Benchmark Python FastAPI + Celery
    logger.info("\n" + "=" * 60)
    logger.info("BENCHMARKING PYTHON BASELINE (FastAPI + Celery + Redis)")
    logger.info("=" * 60)
    
    python_runner = BenchmarkRunner("http://python-api:8000", "python-celery")
    
    for config in configs:
        try:
            result = await python_runner.run_benchmark(**config, task_delay_ms=500)
            results.append(result)
            logger.info(f"Result: {json.dumps(result.to_dict(), indent=2)}")
        except Exception as e:
            logger.error(f"Python benchmark failed for config {config}: {e}")
    
    # Save results
    save_benchmark_results(results)
    print_comparison_report(results)


def save_benchmark_results(results: List[BenchmarkResult]):
    """Save results to JSON file"""
    output = {
        "timestamp": time.time(),
        "results": [r.to_dict() for r in results]
    }
    
    with open("/benchmark_results.json", "w") as f:
        json.dump(output, f, indent=2)
    
    logger.info(f"Results saved to /benchmark_results.json")


def print_comparison_report(results: List[BenchmarkResult]):
    """Print side-by-side comparison"""
    
    print("\n" + "=" * 100)
    print("BENCHMARK COMPARISON REPORT")
    print("=" * 100)
    
    # Group by concurrency level
    by_concurrency = {}
    for result in results:
        key = result.concurrency
        if key not in by_concurrency:
            by_concurrency[key] = {}
        by_concurrency[key][result.platform] = result
    
    for concurrency in sorted(by_concurrency.keys()):
        print(f"\n{'Concurrency Level':^40} {concurrency:^20}")
        print("-" * 100)
        
        platforms = by_concurrency[concurrency]
        
        # Header
        print(f"{'Metric':<25} {'OpenTron':<30} {'Python (Celery)':<30} {'Ratio (Java/Python)':<15}")
        print("-" * 100)
        
        if "opentron-java" in platforms and "python-celery" in platforms:
            java_result = platforms["opentron-java"]
            python_result = platforms["python-celery"]
            
            # Throughput
            java_throughput = java_result.throughput_tasks_per_second
            python_throughput = python_result.throughput_tasks_per_second
            ratio = java_throughput / python_throughput if python_throughput > 0 else 0
            print(f"{'Throughput (tasks/sec)':<25} {java_throughput:>28.2f} {python_throughput:>28.2f} {ratio:>13.2f}x")
            
            # Total Time
            java_time = java_result.total_time_seconds
            python_time = python_result.total_time_seconds
            ratio = python_time / java_time if java_time > 0 else 0
            print(f"{'Total Time (seconds)':<25} {java_time:>28.2f} {python_time:>28.2f} {ratio:>13.2f}x slower")
            
            # Latency p50
            java_p50 = java_result.p50_latency
            python_p50 = python_result.p50_latency
            ratio = python_p50 / java_p50 if java_p50 > 0 else 0
            print(f"{'Latency p50 (ms)':<25} {java_p50:>28.2f} {python_p50:>28.2f} {ratio:>13.2f}x")
            
            # Latency p95
            java_p95 = java_result.p95_latency
            python_p95 = python_result.p95_latency
            ratio = python_p95 / java_p95 if java_p95 > 0 else 0
            print(f"{'Latency p95 (ms)':<25} {java_p95:>28.2f} {python_p95:>28.2f} {ratio:>13.2f}x")
            
            # Latency p99
            java_p99 = java_result.p99_latency
            python_p99 = python_result.p99_latency
            ratio = python_p99 / java_p99 if java_p99 > 0 else 0
            print(f"{'Latency p99 (ms)':<25} {java_p99:>28.2f} {python_p99:>28.2f} {ratio:>13.2f}x")
            
            # Success rate
            java_success = (java_result.successful_tasks / java_result.total_tasks * 100) if java_result.total_tasks > 0 else 0
            python_success = (python_result.successful_tasks / python_result.total_tasks * 100) if python_result.total_tasks > 0 else 0
            print(f"{'Success Rate (%)':<25} {java_success:>28.1f} {python_success:>28.1f}")
    
    print("\n" + "=" * 100)


if __name__ == "__main__":
    asyncio.run(run_comparison_suite())
