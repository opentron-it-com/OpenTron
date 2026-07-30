package org.opentron.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Managed Agents API
 * 
 * Provides full CRUD + lifecycle management for long-running agents.
 * Agents can run on schedules, call tools, access data sources, and send messages.
 */
@RestController
@RequestMapping("/v1/managed-agents")
public class ManagedAgentsController {

    // In-memory store for demo. Production would use a database.
    private static final Map<String, ManagedAgent> agents = new ConcurrentHashMap<>();

    static {
        // Seed with one demo agent
        agents.put("agent-1", new ManagedAgent(
            "agent-1",
            "Daily Brief",
            "personal_deep_research",
            "idle",
            "personal_deep_research",
            "Every morning, give me a briefing on my emails and calendar.",
            "mistral",
            "manual",
            null,
            Arrays.asList("web_search", "email_read"),
            0L,
            0L,
            System.currentTimeMillis() / 1000,
            System.currentTimeMillis() / 1000,
            null,
            0,
            0L,
            0.0,
            true,
            ""
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> listManagedAgents() {
        List<Map<String, Object>> agentList = agents.values().stream()
            .map(ManagedAgent::toMap)
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of(
            "agents", agentList,
            "total", agentList.size()
        ));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createManagedAgent(@RequestBody org.opentron.backend.dto.ManagedAgentCreateRequest payload) {
        String name = payload.getName() == null ? "New Agent" : payload.getName();
        String templateId = payload.getTemplate_id() == null ? "" : payload.getTemplate_id();
        Map<String, Object> config = payload.getConfig() == null ? new HashMap<>() : payload.getConfig();

        String id = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis() / 1000;

        ManagedAgent agent = new ManagedAgent(
            id,
            name,
            templateId.isEmpty() ? "custom" : templateId,
            "idle",
            templateId.isEmpty() ? "custom" : templateId,
            (String) config.getOrDefault("instruction", ""),
            (String) config.getOrDefault("model", "mistral"),
            (String) config.getOrDefault("schedule_type", "manual"),
            (String) config.getOrDefault("schedule_value", null),
            (List<String>) config.getOrDefault("tools", new ArrayList<>()),
            0L,
            0L,
            now,
            now,
            null,
            0,
            0L,
            config.containsKey("budget") ? ((Number) config.get("budget")).doubleValue() : 0.0,
            (Boolean) config.getOrDefault("learning_enabled", false),
            ""
        );
        agent.config = config;

        agents.put(id, agent);
        return ResponseEntity.status(HttpStatus.CREATED).body(agent.toMap());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<Map<String, Object>> getManagedAgent(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(agent.toMap());
    }

    @PatchMapping("/{agentId}")
    public ResponseEntity<Map<String, Object>> updateManagedAgent(@PathVariable String agentId, @RequestBody org.opentron.backend.dto.ManagedAgentUpdateRequest payload) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Update config
        if (payload.getConfig() != null) {
            Map<String, Object> newConfig = payload.getConfig();
            if (newConfig.containsKey("model")) {
                agent.model = (String) newConfig.get("model");
            }
            if (newConfig.containsKey("instruction")) {
                agent.instruction = (String) newConfig.get("instruction");
            }
            agent.config.putAll(newConfig);
        }
        
        agent.updated_at = System.currentTimeMillis() / 1000;
        return ResponseEntity.ok(agent.toMap());
    }

    @PostMapping("/{agentId}/run")
    public ResponseEntity<Map<String, Object>> runManagedAgent(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        
        agent.status = "running";
        agent.last_run_at = System.currentTimeMillis() / 1000;
        
        // Simulate a quick run
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                agent.status = "idle";
                agent.total_runs++;
                agent.total_cost += Math.random() * 0.05;
                agent.summary_memory = "Last run completed successfully. Processed " + agent.total_runs + " queries total.";
            } catch (InterruptedException ignored) {}
        }).start();
        
        return ResponseEntity.ok(agent.toMap());
    }

    @PostMapping("/{agentId}/pause")
    public ResponseEntity<Void> pauseManagedAgent(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        agent.status = "paused";
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{agentId}/resume")
    public ResponseEntity<Void> resumeManagedAgent(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        agent.status = "idle";
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteManagedAgent(@PathVariable String agentId) {
        ManagedAgent removed = agents.remove(agentId);
        if (removed == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{agentId}/recover")
    public ResponseEntity<Map<String, Object>> recoverManagedAgent(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        
        agent.status = "idle";
        agent.summary_memory = "";
        return ResponseEntity.ok(Map.of(
            "success", true,
            "checkpoint", false,
            "message", "Agent recovered to idle state"
        ));
    }

    @PostMapping("/{agentId}/ask")
    public ResponseEntity<Map<String, Object>> askAgent(@PathVariable String agentId, @RequestBody org.opentron.backend.dto.ManagedAgentAskRequest payload) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        
        String question = payload.getQuestion();
        
        agent.status = "running";
        agent.last_run_at = System.currentTimeMillis() / 1000;
        
        // Simulate async run
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                agent.status = "idle";
                agent.total_runs++;
                agent.summary_memory = "Answered: " + question;
            } catch (InterruptedException ignored) {}
        }).start();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "agent_id", agentId
        ));
    }

    @GetMapping("/{agentId}/state")
    public ResponseEntity<Map<String, Object>> getAgentState(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "status", agent.status,
            "total_runs", agent.total_runs,
            "last_run_at", agent.last_run_at,
            "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/{agentId}/messages")
    public ResponseEntity<Map<String, Object>> getAgentMessages(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "messages", new ArrayList<>(),
            "total", 0
        ));
    }

    @GetMapping("/{agentId}/tasks")
    public ResponseEntity<Map<String, Object>> getAgentTasks(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "tasks", new ArrayList<>(),
            "total", 0
        ));
    }

    @PostMapping("/{agentId}/learning/trigger")
    public ResponseEntity<Map<String, Object>> triggerLearning(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "learning_triggered", true,
            "status", "processing"
        ));
    }

    @GetMapping("/{agentId}/learning")
    public ResponseEntity<Map<String, Object>> getLearningStatus(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "learning_enabled", agent.learning_enabled,
            "last_update", agent.updated_at
        ));
    }

    @GetMapping("/{agentId}/traces")
    public ResponseEntity<Map<String, Object>> getAgentTraces(@PathVariable String agentId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "traces", new ArrayList<>(),
            "total", 0
        ));
    }

    @GetMapping("/{agentId}/traces/{traceId}")
    public ResponseEntity<Map<String, Object>> getAgentTrace(@PathVariable String agentId, @PathVariable String traceId) {
        ManagedAgent agent = agents.get(agentId);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "agent_id", agentId,
            "trace_id", traceId,
            "trace", Map.of()
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agent data class
    // ─────────────────────────────────────────────────────────────────────────

    public static class ManagedAgent {
        public String id;
        public String name;
        public String agent_type;  // custom, personal_deep_research, code_reviewer, etc.
        public String status;       // idle, running, paused, error, stalled, needs_attention
        public String template_id;
        public String instruction;
        public String model;
        public String schedule_type; // manual, cron, interval
        public String schedule_value;
        public List<String> tools;
        public long input_tokens;
        public long output_tokens;
        public long created_at;
        public long updated_at;
        public Long last_run_at;
        public long total_runs;
        public long total_cost_cents;
        public double total_cost;
        public boolean learning_enabled;
        public String summary_memory;
        public Map<String, Object> config;

        public ManagedAgent(
            String id, String name, String agent_type, String status, String template_id,
            String instruction, String model, String schedule_type, String schedule_value,
            List<String> tools, long input_tokens, long output_tokens,
            long created_at, long updated_at, Long last_run_at, long total_runs,
            long total_cost_cents, double total_cost, boolean learning_enabled, String summary_memory
        ) {
            this.id = id;
            this.name = name;
            this.agent_type = agent_type;
            this.status = status;
            this.template_id = template_id;
            this.instruction = instruction;
            this.model = model;
            this.schedule_type = schedule_type;
            this.schedule_value = schedule_value;
            this.tools = tools;
            this.input_tokens = input_tokens;
            this.output_tokens = output_tokens;
            this.created_at = created_at;
            this.updated_at = updated_at;
            this.last_run_at = last_run_at;
            this.total_runs = total_runs;
            this.total_cost_cents = total_cost_cents;
            this.total_cost = total_cost;
            this.learning_enabled = learning_enabled;
            this.summary_memory = summary_memory;
            this.config = new HashMap<>();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("name", name);
            result.put("agent_type", agent_type);
            result.put("status", status);
            result.put("template_id", template_id);
            result.put("instruction", instruction);
            result.put("model", model);
            result.put("schedule_type", schedule_type);
            result.put("schedule_value", schedule_value);
            result.put("tools", tools);
            result.put("input_tokens", input_tokens);
            result.put("output_tokens", output_tokens);
            result.put("created_at", created_at);
            result.put("updated_at", updated_at);
            result.put("last_run_at", last_run_at);
            result.put("total_runs", total_runs);
            result.put("total_cost", total_cost);
            result.put("learning_enabled", learning_enabled);
            result.put("summary_memory", summary_memory);
            result.put("current_activity", "");
            result.put("config", config != null ? config : new HashMap<String, Object>());
            return result;
        }
    }
}
