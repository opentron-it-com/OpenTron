package org.opentron.backend.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
@Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class VirtualThreadConcurrencyBenchmark {

    @Param({"100", "500", "1000", "5000", "10000"})
    public int concurrency;

    private ExecutorService executor;
    private AtomicInteger completedTasks;

    @Setup
    public void setup() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        completedTasks = new AtomicInteger(0);
    }

    @TearDown
    public void teardown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Benchmark
    public void benchmarkVirtualThreadThroughput(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(concurrency);
        
        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(500);
                    completedTasks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        bh.consume(completedTasks.get());
    }
}
