# OpenTron Benchmark Suite

Comprehensive benchmarks comparing OpenTron (Java 21 + Virtual Threads) against a traditional Python AI agent stack (FastAPI + Celery + Redis).

## Overview

This benchmark suite validates OpenTron's claims:
- ✅ **10,000+ concurrent agent tasks** on a single JVM
- ✅ **Virtual thread efficiency** under I/O workloads (LLM API calls)
- ✅ **Zero-waste concurrency** with minimal memory footprint
- ✅ **Production-grade throughput** with compile-time safety

## Components

### 1. Java Benchmarks (JMH)

Located in: `java/opentron-java/backend/src/test/java/org/opentron/backend/benchmark/`

- **VirtualThreadConcurrencyBenchmark**: Tests throughput and memory at concurrency levels 100→10K
- **LatencyPercentileBenchmark**: Measures p50/p95/p99 latencies under load

Run JMH benchmarks standalone:
```bash
cd java/opentron-java/backend
mvn clean package -DskipTests
java -jar target/benchmarks.jar
```

### 2. Python Baseline

Located in: `benchmarks/python_baseline/`

- **app.py**: FastAPI server + Celery task definitions
- **Dockerfile**: Containerized Python runtime
- Requires: Redis, 3+ Celery workers for concurrency

### 3. Load Generator

**load_generator.py**: Async load tester that:
- Submits concurrent requests to both OpenTron and Python
- Measures latencies and throughput
- Generates comparison report with percentile analysis

### 4. Monitoring Stack

- **Prometheus**: Metrics collection from Actuator endpoints
- **Grafana**: Dashboards for real-time visualization
- **Redis Exporter**: Task queue metrics

### 5. Docker Compose

**docker-compose.yml**: Complete stack:
- OpenTron backend (Java 21)
- Python API + 3 Celery workers
- Redis (task broker)
- PostgreSQL (state persistence)
- Prometheus + Grafana (monitoring)
- Load generator (automated testing)

## Quick Start

### 1. Prerequisites

```bash
# Ensure Docker and Docker Compose are installed
docker --version
docker-compose --version

# Java 21+ for JMH benchmarks (optional, results included)
java -version
```

### 2. Run Full Benchmark Suite

```bash
cd benchmarks

# Start all services (builds on first run, ~5 minutes)
docker-compose up --pull always

# Wait for services to be healthy (~30 seconds)
# Load generator automatically runs after all services are ready
```

### 3. Monitor in Real-time

**Grafana Dashboard:**
- URL: http://localhost:3000
- Username: `admin`
- Password: `admin`

**Prometheus Queries:**
- URL: http://localhost:9090
- Browse metrics: `http_requests_total`, `jvm_memory_used_bytes`, etc.

### 4. View Results

After load generator completes:

```bash
# Results are saved to
cat benchmark_results.json

# Generate markdown report
python3 analyze_results.py benchmark_results.json

# Read report
cat BENCHMARK_REPORT.md
```

## Running Individual Tests

### JMH Benchmarks (Standalone)

```bash
# Build benchmarks
cd java/opentron-java/backend
mvn clean package -DskipTests

# Run all JMH benchmarks
java -jar target/benchmarks.jar

# Run specific benchmark
java -jar target/benchmarks.jar VirtualThreadConcurrencyBenchmark

# Run with custom JMH options
java -jar target/benchmarks.jar \
  -w 2 \                          # warmup iterations
  -i 5 \                          # measurement iterations
  -f 2 \                          # forks
  -t 1 \                          # threads
  -r 10s                          # time per iteration
```

### Python Baseline (Standalone)

```bash
# Start Redis locally
docker run -d -p 6379:6379 redis:7

# Terminal 1: Start FastAPI server
cd benchmarks/python_baseline
pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8001

# Terminal 2: Start Celery worker
celery -A app.celery_app worker -l info -c 10

# Terminal 3: Load test
cd benchmarks
python3 load_generator.py
```

### Load Generator (Custom Config)

Modify concurrency levels and task counts in `load_generator.py`:

```python
configs = [
    {"concurrency": 10, "total_tasks": 100},
    {"concurrency": 100, "total_tasks": 1000},
    {"concurrency": 1000, "total_tasks": 5000},
    # ... add more
]
```

## Benchmark Methodology

### Test Scenarios

1. **Throughput Under Load**
   - Submits N concurrent tasks
   - Measures total time to completion
   - Calculates tasks/second

2. **Latency Percentiles**
   - Measures p50, p95, p99 latencies
   - Isolates tail latency performance

3. **Memory Footprint**
   - Captures heap usage before/after concurrency ramp-up
   - Calculates bytes-per-task

4. **I/O Efficiency**
   - Each task includes 500ms network latency (simulated LLM API call)
   - Measures how effectively each platform parks threads during I/O

### LLM API Simulation

Both platforms simulate the same workload:
```
Task = 500ms network wait (LLM API) + minimal CPU work
```

This realistic workload highlights:
- **Virtual threads**: Automatically park → free OS thread for other work
- **Python threads**: Block OS threads → require more workers

### Test Duration

- Warmup: 1-2 iterations (JVM JIT compilation)
- Measurement: 3 iterations × 10-30 seconds per config
- Full suite: ~10-15 minutes

## Expected Results

Based on Java 21 virtual thread performance:

| Concurrency | OpenTron Throughput | Python Throughput | Ratio |
|------------|---------------------|-------------------|-------|
| 100 | ~180 tasks/sec | ~80 tasks/sec | **2.2x** |
| 500 | ~210 tasks/sec | ~90 tasks/sec | **2.3x** |
| 1000 | ~220 tasks/sec | ~85 tasks/sec | **2.6x** |
| 5000 | ~200 tasks/sec | ~60 tasks/sec | **3.3x** |
| 10000 | ~180 tasks/sec | ~40 tasks/sec | **4.5x** |

**Note**: Results vary based on hardware and network conditions.

## Troubleshooting

### Docker Compose Won't Start

```bash
# Check disk space
docker system df

# Clean up unused images/volumes
docker system prune -a

# Rebuild from scratch
docker-compose down -v
docker-compose up --build
```

### Load Generator Fails to Connect

```bash
# Verify services are running
docker-compose ps

# Check service logs
docker-compose logs opentron-backend
docker-compose logs python-api

# Wait for services to be fully healthy (30+ seconds)
```

### Out of Memory Errors

**For Python workers:**
```bash
# Increase available memory in docker-compose.yml
services:
  python-worker-1:
    deploy:
      resources:
        limits:
          memory: 2G
```

**For OpenTron:**
```bash
# Increase JVM heap in Dockerfile
ENTRYPOINT [ "java", "-Xmx2G", ... ]
```

## Customization

### Adjusting Concurrency

Edit `load_generator.py`:
```python
configs = [
    {"concurrency": 100, "total_tasks": 1000},
    {"concurrency": 5000, "total_tasks": 25000},  # More extreme
]
```

### Adjusting LLM Latency

Edit `load_generator.py`:
```python
result = await runner.run_benchmark(
    concurrency=1000,
    total_tasks=5000,
    task_delay_ms=1000  # 1 second per task
)
```

### Custom Prometheus Metrics

Edit `monitoring/prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'my-custom-app'
    static_configs:
      - targets: ['my-host:9090']
```

## Performance Tuning

### OpenTron

**Virtual Thread Parallelism** (in Dockerfile):
```bash
-Djdk.virtualThreadScheduler.parallelism=4  # Number of OS threads backing virtual threads
```

**GC Tuning:**
```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+ParallelRefProcEnabled
```

### Python Workers

**Celery Concurrency** (in docker-compose.yml):
```yaml
celery -A app.celery_app worker -c 20  # 20 concurrent tasks per worker
```

**Prefetch Multiplier:**
```python
celery_app.conf.worker_prefetch_multiplier = 1  # Conservative; increase to 4 for throughput
```

## Files Structure

```
benchmarks/
├── docker-compose.yml              # Complete test stack
├── application.yml                 # Spring Boot config
├── load_generator.py               # Async load tester
├── analyze_results.py              # Report generator
├── benchmark_results.json          # Results (generated)
├── BENCHMARK_REPORT.md             # Report (generated)
│
├── python_baseline/
│   ├── app.py                      # FastAPI + Celery
│   ├── requirements.txt            # Dependencies
│   └── Dockerfile                  # Container image
│
└── monitoring/
    ├── prometheus.yml              # Metrics config
    └── grafana/
        ├── dashboards/
        └── datasources/
```

## References

- [Java 21 Virtual Threads](https://openjdk.org/projects/loom/)
- [Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
- [Celery Distributed Task Queue](https://docs.celeryproject.io/)
- [JMH Benchmarking](https://openjdk.org/projects/code-tools/jmh/)
- [OpenTron Architecture](https://opentron.it.com/)

## License

This benchmark suite is provided as-is for evaluation and research purposes.
OpenTron source code is subject to the repository's license terms.

## Questions?

See the main OpenTron repository: https://github.com/open-tron-ai/OpenTron
