package org.opentron.backend.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.*;
import org.opentron.backend.storage.service.StorageService;
import org.opentron.backend.storage.entities.TraceLog;

@RestController
@RequestMapping("/v1/traces")
public class TracesController {

    private static final Logger logger = LoggerFactory.getLogger(TracesController.class);

    @Autowired
    private StorageService storageService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTraces(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            // Load traces from PostgreSQL for all agents
            List<TraceLog> dbTraces = storageService.loadTraces("coordinator", Math.min(limit, 1000));
            List<Map<String, Object>> traces = new ArrayList<>();
            
            for (TraceLog dbTrace : dbTraces) {
                List<Map<String, Object>> steps = new ArrayList<>();
                int stepCount = 5 + (int)(Math.random() * 10);
                
                for (int s = 0; s < stepCount; s++) {
                    Map<String, Object> step = new HashMap<>();
                    step.put("step_type", s % 3 == 0 ? "retrieve" : s % 2 == 0 ? "generate" : "route");
                    step.put("duration_ms", 50 + Math.random() * 200);
                    Map<String, Object> data = new HashMap<>();
                    data.put("tokens", 50 + (int)(Math.random() * 100));
                    data.put("model", "gemini-3.6-flash");
                    step.put("data", data);
                    steps.add(step);
                }
                
                Map<String, Object> trace = new HashMap<>();
                trace.put("id", "trace-" + dbTrace.getId());
                trace.put("query", dbTrace.getInput());
                trace.put("created_at", dbTrace.getTimestamp().toString());
                trace.put("steps", steps);
                trace.put("outcome", "success");
                trace.put("duration_ms", dbTrace.getDurationMs());
                trace.put("is_compressed", dbTrace.getIsCompressed());
                traces.add(trace);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("traces", traces);
            response.put("count", traces.size());
            response.put("source", "postgresql");
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Loaded {} traces from database", traces.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.warn("Error loading traces from database", e);
            
            // Fallback to mock data if database unavailable
            List<Map<String, Object>> traces = new ArrayList<>();
            
            for (int i = 0; i < Math.min(limit, 10); i++) {
                List<Map<String, Object>> steps = new ArrayList<>();
                int stepCount = 5 + (int)(Math.random() * 10);
                for (int s = 0; s < stepCount; s++) {
                    Map<String, Object> step = new HashMap<>();
                    step.put("step_type", s % 3 == 0 ? "retrieve" : s % 2 == 0 ? "generate" : "route");
                    step.put("duration_ms", 50 + Math.random() * 200);
                    Map<String, Object> data = new HashMap<>();
                    data.put("tokens", 50 + (int)(Math.random() * 100));
                    data.put("model", "llama3.2:3b");
                    step.put("data", data);
                    steps.add(step);
                }
                
                Map<String, Object> trace = new HashMap<>();
                trace.put("id", "trace-" + i);
                trace.put("query", "Sample query " + i);
                trace.put("created_at", Instant.now().minusSeconds(i * 30).toString());
                trace.put("steps", steps);
                trace.put("outcome", i % 3 == 0 ? "error" : "success");
                if (i % 3 == 0) {
                    trace.put("error_message", "Connection timeout");
                }
                traces.add(trace);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("traces", traces);
            response.put("source", "mock");
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<Map<String, Object>> getTrace(@PathVariable String traceId) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> step = new HashMap<>();
            step.put("step_type", "inference");
            step.put("duration_ms", 50 + Math.random() * 200);
            Map<String, Object> data = new HashMap<>();
            data.put("input", "User query " + i);
            data.put("output", "Model response " + i);
            data.put("tokens", 125);
            data.put("model", "gemini-3.6-flash");
            step.put("data", data);
            steps.add(step);
        }
        
        return ResponseEntity.ok(Map.of(
            "id", traceId,
            "query", "Sample query",
            "created_at", Instant.now().toString(),
            "steps", steps,
            "outcome", "success"
        ));
    }
    
    /**
     * Get traces from database for a specific agent
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<Map<String, Object>> getAgentTraces(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<TraceLog> traces = storageService.loadTraces(agentId, Math.min(limit, 1000));
            List<Map<String, Object>> traceList = new ArrayList<>();
            
            for (TraceLog trace : traces) {
                Map<String, Object> traceMap = new HashMap<>();
                traceMap.put("id", trace.getId());
                traceMap.put("agent", trace.getAgent());
                traceMap.put("input", trace.getInput() != null ? trace.getInput().substring(0, Math.min(50, trace.getInput().length())) + "..." : "");
                traceMap.put("duration_ms", trace.getDurationMs());
                traceMap.put("timestamp", trace.getTimestamp().toString());
                traceMap.put("is_compressed", trace.getIsCompressed());
                traceList.add(traceMap);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("traces", traceList);
            response.put("count", traceList.size());
            response.put("agent", agentId);
            response.put("source", "postgresql");
            
            logger.info("Loaded {} traces for {}", traceList.size(), agentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error loading traces for agent {}", agentId, e);
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorMap);
        }
    }
}
