package org.opentron.backend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.opentron.backend.agents.AgentService;
import org.opentron.backend.agents.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/agents")
@CrossOrigin(origins = "*")
public class AgentBenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(AgentBenchmarkController.class);
    
    @Autowired(required = false)
    private AgentService agentService;

    @PostMapping("/query/blocking")
    public ResponseEntity<Map<String, Object>> queryAgentBlocking(
            @RequestParam(required = false, defaultValue = "test prompt") String prompt,
            @RequestParam(required = false, defaultValue = "500") int delayMs
    ) {
        try {
            long startNs = System.nanoTime();
            Thread.sleep(delayMs);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "completed");
            response.put("response", "Response to: " + prompt);
            response.put("latency_ms", (double) elapsedMs);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.debug("Agent query completed in {}ms", elapsedMs);
            return ResponseEntity.ok(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Agent query interrupted", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", "Interrupted: " + e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Agent query failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> submitAgentQuery(
            @RequestParam(required = false, defaultValue = "test prompt") String prompt,
            @RequestParam(required = false, defaultValue = "500") int delayMs
    ) {
        try {
            String agentId = java.util.UUID.randomUUID().toString();
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                    logger.debug("Async query completed for agent {}", agentId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Async query interrupted", e);
                }
            });
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "submitted");
            response.put("agent_id", agentId);
            response.put("message", "Query submitted for async processing");
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            logger.error("Failed to submit query", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @PostMapping("/stress-test")
    public ResponseEntity<Map<String, Object>> stressTest(
            @RequestParam(required = false, defaultValue = "100") int taskCount,
            @RequestParam(required = false, defaultValue = "500") int delayMs
    ) {
        try {
            long startNs = System.nanoTime();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            logger.info("Starting stress test: {} tasks x {}ms", taskCount, delayMs);
            
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Stress test interrupted");
            }
            
            executor.shutdown();
            
            long totalNs = System.nanoTime() - startNs;
            long totalMs = totalNs / 1_000_000;
            double throughput = (taskCount / (totalMs / 1000.0));
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "completed");
            response.put("total_tasks", taskCount);
            response.put("total_time_ms", totalMs);
            response.put("throughput_tasks_per_sec", throughput);
            response.put("avg_latency_ms", totalMs / (double) taskCount);
            
            logger.info("Stress test complete: {} tasks in {}ms ({} tasks/sec)",
                    taskCount, totalMs, String.format("%.2f", throughput));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Stress test failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/memory-profile")
    public ResponseEntity<Map<String, Object>> memoryProfile(
            @RequestParam(required = false, defaultValue = "1000") int taskCount
    ) {
        try {
            Runtime rt = Runtime.getRuntime();
            System.gc();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            long beforeMemory = rt.totalMemory() - rt.freeMemory();
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Memory profiling interrupted");
            }
            
            long afterMemory = rt.totalMemory() - rt.freeMemory();
            long memoryUsed = afterMemory - beforeMemory;
            double bytesPerTask = (double) memoryUsed / taskCount;
            executor.shutdown();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "completed");
            response.put("task_count", taskCount);
            response.put("memory_used_bytes", memoryUsed);
            response.put("bytes_per_task", bytesPerTask);
            response.put("before_memory_mb", beforeMemory / (1024 * 1024));
            response.put("after_memory_mb", afterMemory / (1024 * 1024));
            
            logger.info("Memory profile: {} tasks used {} bytes ({} bytes/task)",
                    taskCount, memoryUsed, String.format("%.0f", bytesPerTask));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Memory profiling failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }
}
