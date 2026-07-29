package org.opentron.backend.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.*;
import java.util.Arrays;

@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1)
@Warmup(iterations = 1, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class LatencyPercentileBenchmark {

    @Param({"100", "1000", "5000", "10000"})
    public int concurrency;

    private ExecutorService executor;
    private long[] latencies;
    private int latencyIndex = 0;

    @Setup
    public void setup() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        latencies = new long[concurrency * 10];
    }

    @TearDown
    public void teardown() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        reportPercentiles();
    }

    @Benchmark
    public void benchmarkTaskLatency(Blackhole bh) throws InterruptedException {
        long startNs = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        long endNs = System.nanoTime();
        long latencyMs = (endNs - startNs) / 1_000_000;
        
        if (latencyIndex < latencies.length) {
            latencies[latencyIndex++] = latencyMs;
        }
        
        bh.consume(latencyMs);
    }

    private void reportPercentiles() {
        if (latencyIndex == 0) return;
        
        Arrays.sort(latencies, 0, latencyIndex);
        
        long p50 = latencies[Math.max(0, (int)(latencyIndex * 0.50))];
        long p95 = latencies[Math.max(0, (int)(latencyIndex * 0.95))];
        long p99 = latencies[Math.max(0, (int)(latencyIndex * 0.99))];
        
        System.out.println("\n=== Latency Percentiles ===");
        System.out.println("Concurrency: " + concurrency);
        System.out.println("Samples: " + latencyIndex);
        System.out.println("p50: " + p50 + "ms");
        System.out.println("p95: " + p95 + "ms");
        System.out.println("p99: " + p99 + "ms");
    }
}
