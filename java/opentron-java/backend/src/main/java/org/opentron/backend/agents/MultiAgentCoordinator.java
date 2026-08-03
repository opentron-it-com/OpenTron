package org.opentron.backend.agents;

import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opentron.backend.telemetry.TelemetryService;
import org.opentron.backend.util.CloudModelService;
import org.opentron.backend.util.OllamaCliService;
import org.opentron.backend.util.HuggingFaceService;
import org.opentron.backend.memory.MemoryService;
import org.opentron.backend.memory.MemorySearchRequest;
import org.opentron.backend.memory.MemoryEntry;
import org.opentron.backend.services.ModelSelectorService;
import org.opentron.backend.util.GeminiCodeModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Multi-Agent Coordinator System
 * Tron coordinates with specialist agents: Backend, Frontend, DevOps, QA, Knowledge
 */
@Component
public class MultiAgentCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(MultiAgentCoordinator.class);
    private final OllamaCliService ollamaService;
    private final HuggingFaceService huggingFaceService;
    private final CloudModelService cloudModelService;
    private final ModelSelectorService modelSelectorService;
    private final org.opentron.backend.services.WebSearchService webSearchService;
    @Autowired(required = false)
    private TelemetryService telemetryService;
    @Autowired(required = false)
    private MemoryService memoryService;
    @Autowired(required = false)
    private GeminiCodeModelService geminiService;
    private final Map<String, SpecializedAgent> agents = new ConcurrentHashMap<>();
    private final BlockingQueue<AgentMessage> messageQueue = new LinkedBlockingQueue<>();

    public SpecializedAgent getAgent(String name) {
        return agents.get(name);
    }

    public MultiAgentCoordinator(OllamaCliService ollamaService, HuggingFaceService huggingFaceService,
                                 ModelSelectorService modelSelectorService, CloudModelService cloudModelService,
                                 org.opentron.backend.services.WebSearchService webSearchService) {
        this.ollamaService = ollamaService;
        this.huggingFaceService = huggingFaceService;
        this.modelSelectorService = modelSelectorService;
        this.cloudModelService = cloudModelService;
        this.webSearchService = webSearchService;
        try {
            initializeAgents();
            startMessageProcessor();
            logger.info("Initialized with intelligent model selection");
        } catch (Exception e) {
            logger.error("Initialization error", e);
            if (agents.isEmpty()) {
                initializeAgentsWithDefaults();
            }
        }
    }

    private void initializeAgentsWithDefaults() {
        logger.info("Using fallback initialization with default models");
        AgentLLMBridge llmBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, "mistral", null, webSearchService);
        agents.put("coordinator", new CoordinatorAgent(llmBridge));
        agents.put("backend",    new BackendSpecialist(llmBridge));
        agents.put("frontend",   new FrontendSpecialist(llmBridge));
        agents.put("devops",     new DevOpsAgent(llmBridge));
        agents.put("qa",         new QAAgent(llmBridge));
        agents.put("knowledge",  new KnowledgeAgent(llmBridge, null));
    }

    private void initializeAgents() {
        initializeAgents(null);
    }

    private void initializeAgents(Map<String, String> apiKeyOverrides) {
        logger.info("Initializing with intelligent model selection...");

        String coordinatorModel = "mistral";
        String backendModel  = modelSelectorService.selectBestModel("backend",  apiKeyOverrides);
        String frontendModel = modelSelectorService.selectBestModel("frontend", apiKeyOverrides);
        String qaModel       = modelSelectorService.selectBestModel("qa",       apiKeyOverrides);
        String devopsModel   = modelSelectorService.selectBestModel("devops",   apiKeyOverrides);
        String knowledgeModel = modelSelectorService.selectBestModel("knowledge", apiKeyOverrides);

        logger.info("Model assignments: Backend={}, Frontend={}, QA={}, DevOps={}, Knowledge={}",
                backendModel, frontendModel, qaModel, devopsModel, knowledgeModel);

        AgentLLMBridge coordinatorBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, coordinatorModel, apiKeyOverrides, webSearchService);
        agents.put("coordinator", new CoordinatorAgent(coordinatorBridge));

        AgentLLMBridge backendBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, backendModel, apiKeyOverrides, webSearchService);
        agents.put("backend", new BackendSpecialist(backendBridge));

        AgentLLMBridge frontendBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, frontendModel, apiKeyOverrides, webSearchService);
        agents.put("frontend", new FrontendSpecialist(frontendBridge));

        AgentLLMBridge qaBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, qaModel, apiKeyOverrides, webSearchService);
        agents.put("qa", new QAAgent(qaBridge));

        AgentLLMBridge devopsBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, devopsModel, apiKeyOverrides, webSearchService);
        agents.put("devops", new DevOpsAgent(devopsBridge));

        // Knowledge agent now gets its own optimized model for personal knowledge management
        AgentLLMBridge knowledgeBridge = new AgentLLMBridge(
            ollamaService, huggingFaceService, cloudModelService, knowledgeModel, apiKeyOverrides, webSearchService);
        agents.put("knowledge", new KnowledgeAgent(knowledgeBridge, memoryService));

        logger.info("Initialized {} agents with optimized models", agents.size());
    }

    private void startMessageProcessor() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    AgentMessage msg = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        SpecializedAgent agent = agents.get(msg.targetAgent);
                        if (agent != null) {
                            long start = System.currentTimeMillis();
                            Object result = agent.process(msg);
                            long elapsed = System.currentTimeMillis() - start;
                            logger.debug("[{}] Processed in {}ms:{}", msg.targetAgent, elapsed, result);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Callback interface for streaming agent progress
    // -------------------------------------------------------------------------

    public interface StreamingCoordinatorCallback {
        void onAgentStart(String agent);
        void onAgentChunk(String agent, String chunk);
        void onAgentDone(String agent, Map<String, Object> result);
        void onAgentError(String agent, String error);
        void onStatus(String status);
    }

    // -------------------------------------------------------------------------
    // processRequest
    // -------------------------------------------------------------------------

    public Map<String, Object> processRequest(String userRequest, String context) {
        return processRequest(userRequest, context, null, null);
    }

    public Map<String, Object> processRequest(String userRequest, String context,
                                               StreamingCoordinatorCallback callback) {
        return processRequest(userRequest, context, callback, null);
    }

    public Map<String, Object> processRequest(String userRequest, String context,
                                               StreamingCoordinatorCallback callback,
                                               Map<String, String> apiKeyOverrides) {
        logger.info("Processing coordinator request with overrides={}", apiKeyOverrides);
        long start = System.currentTimeMillis();
        try {
            if (apiKeyOverrides != null && !apiKeyOverrides.isEmpty()) {
                logger.info("Reinitializing agent models using API-key overrides");
                agents.clear();
                initializeAgents(apiKeyOverrides);
            } else if (agents.isEmpty()) {
                initializeAgentsWithDefaults();
            }

            CoordinatorAgent coordinator = (CoordinatorAgent) agents.get("coordinator");
            if (coordinator == null) throw new RuntimeException("Coordinator agent not found");

            Map<String, Object> result = coordinator.coordinate(userRequest, context, this, callback);
            long totalTime = System.currentTimeMillis() - start;

            Map<String, Object> response = new HashMap<>();
            response.put("result",      result);
            response.put("elapsed_ms",  totalTime);
            response.put("agents_used", result.get("agents_used"));
            logger.info("Coordinator completed in {}ms", totalTime);
            return response;
        } catch (Exception e) {
            logger.error("Coordinator error", e);
            long totalTime = System.currentTimeMillis() - start;
            Map<String, Object> response = new HashMap<>();
            response.put("error",      e.getMessage());
            response.put("elapsed_ms", totalTime);
            response.put("status",     "error");
            return response;
        }
    }

    public void sendMessage(AgentMessage msg) {
        try {
            messageQueue.offer(msg);
        } catch (Exception e) {
            logger.error("Coordinator message error", e);
        }
    }

    public Map<String, Object> getAgentStatuses() {
        try {
            if (agents.isEmpty()) initializeAgentsWithDefaults();
            Map<String, Object> statuses = new HashMap<>();
            for (Map.Entry<String, SpecializedAgent> entry : agents.entrySet()) {
                statuses.put(entry.getKey(), entry.getValue().getStatus());
            }
            return statuses;
        } catch (Exception e) {
            logger.error("Error building agent statuses", e);
            return new HashMap<>();
        }
    }

    // -------------------------------------------------------------------------
    // SpecializedAgent base
    // -------------------------------------------------------------------------

    public abstract static class SpecializedAgent {
        protected String name;
        protected List<String> skills = new ArrayList<>();
        protected long lastExecutedTime = 0;
        protected AgentLLMBridge llmBridge;

        public SpecializedAgent(AgentLLMBridge llmBridge) {
            this.llmBridge = llmBridge;
        }

        public abstract Object process(AgentMessage msg);

        protected List<String> asStringList(Object value) {
            if (value instanceof List<?> values) {
                List<String> result = new ArrayList<>();
                for (Object item : values) {
                    if (item != null) result.add(String.valueOf(item));
                }
                return result;
            }
            return List.of();
        }

        protected Map<String, Object> handleEmptyResponse(Map<String, Object> result, String agentName) {
            if (result == null) return result;
            String response = result.getOrDefault("response", "").toString().trim();
            if (response.isEmpty()) {
                result.put("response",
                        "No relevant " + agentName + " tasks identified for this request.");
                Logger log = LoggerFactory.getLogger(this.getClass());
                Object tokensObj = result.get("tokens_used");
                long tokensUsed = tokensObj instanceof Number ? ((Number) tokensObj).longValue() : 0;
                log.debug("{} agent: empty response handled - {} tokens used", agentName, tokensUsed);
            }
            return result;
        }

        protected Map<String, Object> invokeScopedLLM(String role, String task, String context,
                                                       List<String> focus, List<String> ignore,
                                                       List<String> constraints, String agentKey,
                                                       StreamingCoordinatorCallback callback) {

            String internetSearchPrompt = "Act as an expert internet researcher.\r\n" +
                                "\r\n" +
                                "Conduct a deep web investigation on [TOPIC].\r\n" +
                                "\r\n" +
                                "Use only authoritative sources whenever possible.\r\n" +
                                "\r\n" +
                                "Methodology:\r\n" +
                                "1. Search broadly.\r\n" +
                                "2. Verify important claims with multiple sources.\r\n" +
                                "3. Locate official documentation and primary sources.\r\n" +
                                "4. Identify misinformation or unverified claims.\r\n" +
                                "5. Compare competing perspectives objectively.\r\n" +
                                "6. Present findings with citations and links.\r\n" +
                                "\r\n" +
                                "Deliver:\r\n" +
                                "- Executive summary\r\n" +
                                "- Detailed analysis\r\n" +
                                "- Evidence table\r\n" +
                                "- Key insights\r\n" +
                                "- Risks and limitations\r\n" +
                                "- Source list\r\n" +
                                "\r\n" +
                                "Do not make assumptions.\r\n" +
                                "Clearly distinguish verified facts from opinions." +
                                "You have internet access and can fetch external content, perform google search, research topics, and integrate with APIs. ";
            
            // Get agent-specific system prompt from new AgentSystemPrompts class
            String baseSystemPrompt = AgentSystemPrompts.getSystemPrompt(agentKey);
            // Enhance with focus/constraints specific to this request
            String systemPrompt = AgentSystemPrompts.enhanceWithAgentContext(baseSystemPrompt, agentKey, focus, constraints) + "\n\n" + internetSearchPrompt;

            String userQuestion = "Task: " + task
                    + "\n\nRelevant context:\n" + context
                    + "\n\nRespond with a helpful specialist answer.";
            final StringBuilder acc = new StringBuilder();
            Map<String, Object> result = llmBridge.queryLLMStream(systemPrompt, userQuestion, 256, chunk -> {
                acc.append(chunk);
                if (callback != null) callback.onAgentChunk(agentKey, chunk);
            });
            if (result != null
                    && (!result.containsKey("response")
                        || String.valueOf(result.get("response")).isBlank())) {
                result.put("response", acc.toString());
            }
            result.put("agent", agentKey);
            return result;
        }

        public Map<String, Object> getStatus() {
            return Map.of("name", name, "skills", skills, "last_executed_ms", lastExecutedTime);
        }
    }

    // -------------------------------------------------------------------------
    // CoordinatorAgent
    // -------------------------------------------------------------------------

    public static class CoordinatorAgent extends SpecializedAgent {
        public CoordinatorAgent(AgentLLMBridge llmBridge) {
            super(llmBridge);
            this.name = "Coordinator";
            this.skills = Arrays.asList(
                "analyze_requirements", "delegate_to_specialists",
                "monitor_progress", "aggregate_results", "error_recovery");
        }

        @Override
        public Object process(AgentMessage msg) {
            return Map.of("status", "ok", "message", "Coordinator received message");
        }

        public Map<String, Object> coordinate(String userRequest, String context,
                                               MultiAgentCoordinator coordinator,
                                               StreamingCoordinatorCallback callback) {
            long start = System.currentTimeMillis();
            List<String> agentsUsed  = new ArrayList<>();
            Map<String, Object> taskResults = new HashMap<>();

            TelemetryService ts = coordinator.telemetryService;
            if (ts != null) ts.recordRequest();
            if (callback != null) callback.onStatus("Analyzing request and routing to specialist agents...");

            List<String> requiredAgents = analyzeRequest(userRequest, context);
            agentsUsed.addAll(requiredAgents);
            logger.info("Required agents: {}", requiredAgents);

            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (String agentName : requiredAgents) {
                    futures.add(executor.submit(() -> {
                        if (callback != null) callback.onAgentStart(agentName);
                        SpecializedAgent agent = coordinator.agents.get(agentName);
                        if (agent != null) {
                            List<String> focusedSkills =
                                    inferSkillsForAgent(agentName, userRequest, context);
                            Map<String, Object> payload =
                                    buildSpecialistTaskPayload(userRequest, context, agentName, focusedSkills);
                            AgentMessage msg = new AgentMessage(agentName, "task", payload, callback);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> result = (Map<String, Object>) agent.process(msg);
                            if (callback != null) {
                                if ("error".equals(result.get("status")))
                                    callback.onAgentError(agentName,
                                            (String) result.getOrDefault("error", "unknown"));
                                else
                                    emitAgentChunks(agentName, result, callback);
                            }
                            return result;
                        }
                        if (callback != null) callback.onAgentError(agentName, "Agent not found");
                        return Map.of("error", "Agent not found");
                    }));
                }

                for (int i = 0; i < futures.size(); i++) {
                    try {
                        Map<String, Object> result = futures.get(i).get();
                        taskResults.put(requiredAgents.get(i), result);
                        if (ts != null && result != null && result.containsKey("tokens_used")) {
                            Object tokensObj = result.get("tokens_used");
                            if (tokensObj instanceof Number n) ts.addTokens(n.longValue());
                        }
                    } catch (Exception e) {
                        logger.error("{} agent error", requiredAgents.get(i), e);
                        taskResults.put(requiredAgents.get(i),
                                Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown"));
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            String tronResponse = buildTronChatResponse(userRequest, agentsUsed, taskResults, elapsed);

            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("status",      "completed");
            finalResult.put("agents_used", agentsUsed);
            finalResult.put("results",     taskResults);
            finalResult.put("elapsed_ms",  elapsed);
            finalResult.put("tron_response", tronResponse);
            finalResult.put("message",     tronResponse);
            this.lastExecutedTime = elapsed;
            return finalResult;
        }

        private Map<String, Object> buildSpecialistTaskPayload(String userRequest, String context,
                                                                String agentName,
                                                                List<String> focusedSkills) {
            String normalizedContext = context == null ? "" : context.trim();
            String requestSummary    = userRequest == null ? "" : userRequest.trim();
            List<String> focus       = new ArrayList<>();
            List<String> ignore      = new ArrayList<>();
            List<String> constraints = new ArrayList<>(Arrays.asList(
                    "stay concise", "address only the relevant domain", "avoid unrelated suggestions"));

            switch (agentName) {
                case "backend"    -> {
                    focus.addAll(List.of("thread-safety", "race conditions", "synchronization", "API design", "database access", "caching", "performance", "error handling", "internet access", "external APIs"));
                    constraints.add("CRITICAL: Always analyze code for thread-safety bugs - race conditions, atomicity violations, visibility issues, and deadlock risks. Show specific scenarios where 2+ threads cause failures. Provide fixed code with synchronization explanation.");
                    ignore.addAll(List.of("UI", "React", "CSS", "visual design"));
                }
                case "frontend"   -> {
                    focus.addAll(List.of("React", "component design", "state flow", "stale closures", "memory leaks", "responsive behavior", "accessibility", "internet access", "external APIs"));
                    constraints.add("CRITICAL: Check for React state management issues - stale closures, missing useEffect dependencies, memory leaks in subscriptions, and proper cleanup. Provide working component examples with TypeScript.");
                    ignore.addAll(List.of("backend services", "database schema", "deployment"));
                }
                case "qa"         -> {
                    focus.addAll(List.of("edge cases", "unit tests", "integration tests", "debugging", "regression risks", "security vulnerabilities", "error handling", "verification steps", "internet access", "external APIs"));
                    constraints.add("CRITICAL: Provide specific test cases with assertions. Identify ALL edge cases and failure scenarios. Show reproducible bug scenarios. Explain root causes, not just symptoms.");
                    ignore.addAll(List.of("visual styling", "deployment automation"));
                }
                case "devops"     -> {
                    focus.addAll(List.of("resource optimization", "monitoring", "observability", "health checks", "reliability", "scalability", "resource usage", "internet access", "external APIs"));
                    constraints.add("CRITICAL: Analyze resource usage patterns. Suggest infrastructure improvements with observability metrics. Include health check endpoints and alerting thresholds. Provide Docker/Kubernetes examples.");
                    ignore.addAll(List.of("UI implementation", "business logic"));
                }
                case "knowledge"  -> {
                    focus.addAll(List.of("personal data", "emails", "documents", "notes", "calendar", "contacts", "internet access", "external APIs", "user memory"));
                    ignore.addAll(List.of("code generation", "infrastructure"));
                }
                default           -> focus.add("core request");
            }
            if (!focusedSkills.isEmpty()) focus.addAll(focusedSkills);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agent",            agentName);
            payload.put("task",             "Respond to the request from the perspective of the " + agentName + " specialist.");
            payload.put("request_summary",  requestSummary);
            payload.put("request",          requestSummary);
            payload.put("focus",            focus);
            payload.put("ignore",           ignore);
            payload.put("relevant_context", normalizedContext.isBlank() ? requestSummary : normalizedContext);
            payload.put("constraints",      constraints);
            payload.put("domain",           agentName);
            return payload;
        }

        private List<String> inferSkillsForAgent(String agentName, String request, String context) {
            String combined = (request == null ? "" : request) + " " + (context == null ? "" : context);
            String lower    = combined.toLowerCase(Locale.ROOT);
            return switch (agentName) {
                case "backend"   -> {
                    List<String> s = new ArrayList<>();
                    if (lower.contains("spring") || lower.contains("java") || lower.contains("api")
                            || lower.contains("database") || lower.contains("cache") || lower.contains("thread") || lower.contains("concurr")) {
                        s.add("spring_boot_configuration");
                        s.add("database_query_optimization");
                        s.add("concurrent_programming");
                    }
                    yield s;
                }
                case "frontend"  -> {
                    List<String> s = new ArrayList<>();
                    if (lower.contains("react") || lower.contains("ui") || lower.contains("component")
                            || lower.contains("typescript") || lower.contains("frontend")) {
                        s.add("react_optimization");
                        s.add("component_design");
                    }
                    yield s;
                }
                case "qa"        -> {
                    List<String> s = new ArrayList<>();
                    if (lower.contains("test") || lower.contains("debug")
                            || lower.contains("fix") || lower.contains("review")) {
                        s.add("debugging");
                        s.add("integration_testing");
                    }
                    yield s;
                }
                case "devops"    -> {
                    List<String> s = new ArrayList<>();
                    if (lower.contains("monitor") || lower.contains("metric")
                            || lower.contains("health") || lower.contains("deploy")
                            || lower.contains("performance")) {
                        s.add("performance_monitoring");
                        s.add("health_checks");
                    }
                    yield s;
                }
                case "knowledge" -> {
                    List<String> s = new ArrayList<>();
                    s.add("search_memory");
                    s.add("search_documents");
                    s.add("web_research");
                    yield s;
                }
                default          -> List.of();
            };
        }

        private void emitAgentChunks(String agentName, Map<String, Object> result,
                                      StreamingCoordinatorCallback callback) {
            String text = result.containsKey("response") ? String.valueOf(result.get("response")) : "";
            if (text == null || text.isBlank()) { callback.onAgentDone(agentName, result); return; }
            String normalized = text.trim();
            if (normalized.length() <= 1) {
                callback.onAgentChunk(agentName, normalized);
                callback.onAgentDone(agentName, result);
                return;
            }
            // Stream by complete sentences/words - split on spaces and punctuation
            // Emit ~40-80 char chunks ending on word boundaries for natural reading flow
            int targetChunkSize = 50;
            int pos = 0;
            while (pos < normalized.length()) {
                int end = Math.min(pos + targetChunkSize, normalized.length());
                // If not at end, find last space or punctuation before end to avoid cutting words
                if (end < normalized.length()) {
                    int lastSpace = normalized.lastIndexOf(' ', end);
                    int lastPunct = Math.max(normalized.lastIndexOf('.', end), 
                                           Math.max(normalized.lastIndexOf('!', end),
                                                  normalized.lastIndexOf('?', end)));
                    int breakPoint = Math.max(lastSpace, lastPunct);
                    if (breakPoint > pos + 10) { // Only use it if we've made reasonable progress
                        end = breakPoint + 1;
                    }
                }
                String chunk = normalized.substring(pos, end).trim();
                if (!chunk.isEmpty()) {
                    callback.onAgentChunk(agentName, chunk + (end < normalized.length() ? " " : ""));
                }
                pos = end;
            }
            callback.onAgentDone(agentName, result);
        }

        private String buildTronChatResponse(String userRequest, List<String> agentsUsed,
                                              Map<String, Object> taskResults, long elapsedMs) {
            StringBuilder sb = new StringBuilder();
            sb.append("I've analyzed your request and coordinated with my specialist agents.\n\n");
            sb.append("**Analysis Results:**\n\n");
            for (String agent : agentsUsed) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) taskResults.get(agent);
                if (result != null) {
                    sb.append("**").append(Character.toUpperCase(agent.charAt(0)))
                      .append(agent.substring(1)).append(" Agent:**\n");
                    if (result.containsKey("response"))
                        sb.append(result.get("response"));
                    else if (result.containsKey("error"))
                        sb.append("Error: ").append(result.get("error"));
                    else
                        sb.append(result);
                    sb.append("\n\n");
                }
            }
            sb.append("*Processed by: Tron with ").append(agentsUsed.size())
              .append(" specialist agents in ").append(elapsedMs).append("ms*");
            return sb.toString();
        }

        private List<String> analyzeRequest(String request, String context) {
            List<String> agentList = new ArrayList<>();
            String lower = request.toLowerCase();
            
            // Check for SPECIFIC TECHNICAL DOMAINS ONLY
            // Backend code
            if (lower.contains("backend") || lower.contains("java") || lower.contains("spring") ||
                lower.contains("database") || lower.contains("api") || lower.contains("cache") ||
                lower.contains("server") || lower.contains("http") || lower.contains("thread") || lower.contains("concurrent")) {
                agentList.add("backend");
            }
            // Frontend code
            else if (lower.contains("frontend") || lower.contains("react") || lower.contains("typescript") ||
                     lower.contains("component") || lower.contains("ui") || lower.contains("css")) {
                agentList.add("frontend");
            }
            // QA/Testing - ONLY for explicit test/debug keywords
            else if (lower.contains("test") || lower.contains("debug") || lower.contains("unit test") ||
                     lower.contains("test case")) {
                agentList.add("qa");
            }
            // DevOps - infrastructure specific
            else if (lower.contains("devops") || lower.contains("kubernetes") || lower.contains("docker") ||
                     lower.contains("monitor") || lower.contains("metrics")) {
                agentList.add("devops");
            }
            // DEFAULT: Everything else goes to Knowledge
            else {
                agentList.add("knowledge");
            }
            
            return agentList;
        }
    }

    // -------------------------------------------------------------------------
    // Specialist agents
    // -------------------------------------------------------------------------

    public static class BackendSpecialist extends SpecializedAgent {
        public BackendSpecialist(AgentLLMBridge llmBridge) {
            super(llmBridge);
            this.name   = "Backend";
            this.skills = Arrays.asList("java_optimization", "spring_boot_configuration",
                "database_query_optimization", "api_design", "persistence_layer",
                "cache_optimization", "concurrent_programming", "error_handling",
                "web_research", "api_integration", "fetch_external_content");
        }
        @Override public Object process(AgentMessage msg) {
            long start = System.currentTimeMillis();
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) msg.payload;
            Map<String, Object> r = invokeScopedLLM("backend optimization expert",
                str(p, "task", "request"), str(p, "relevant_context", "context"),
                asl(p.get("focus")), asl(p.get("ignore")), asl(p.get("constraints")),
                "backend", msg.streamCallback);
            this.lastExecutedTime = System.currentTimeMillis() - start;
            return handleEmptyResponse(r, "Backend");
        }
        private String str(Map<String, Object> p, String k1, String k2) {
            return String.valueOf(p.getOrDefault(k1, p.getOrDefault(k2, "")));
        }
        private List<String> asl(Object v) { return asStringList(v); }
    }

    public static class FrontendSpecialist extends SpecializedAgent {
        public FrontendSpecialist(AgentLLMBridge llmBridge) {
            super(llmBridge);
            this.name   = "Frontend";
            this.skills = Arrays.asList("react_optimization", "component_design", "state_management",
                "performance_tuning", "css_optimization", "accessibility",
                "responsive_design", "bundle_optimization",
                "web_research", "api_integration", "fetch_external_content");
        }
        @Override public Object process(AgentMessage msg) {
            long start = System.currentTimeMillis();
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) msg.payload;
            Map<String, Object> r = invokeScopedLLM("frontend React expert",
                str(p, "task", "request"), str(p, "relevant_context", "context"),
                asStringList(p.get("focus")), asStringList(p.get("ignore")), asStringList(p.get("constraints")),
                "frontend", msg.streamCallback);
            this.lastExecutedTime = System.currentTimeMillis() - start;
            return handleEmptyResponse(r, "Frontend");
        }
        private String str(Map<String, Object> p, String k1, String k2) {
            return String.valueOf(p.getOrDefault(k1, p.getOrDefault(k2, "")));
        }
    }

    public static class DevOpsAgent extends SpecializedAgent {
        public DevOpsAgent(AgentLLMBridge llmBridge) {
            super(llmBridge);
            this.name   = "DevOps";
            this.skills = Arrays.asList("performance_monitoring", "log_aggregation", "metrics_collection",
                "alerting", "health_checks", "capacity_planning",
                "resource_optimization", "skill_synchronization",
                "web_research", "api_integration", "fetch_external_content");
        }
        @Override public Object process(AgentMessage msg) {
            long start = System.currentTimeMillis();
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) msg.payload;
            Map<String, Object> r = invokeScopedLLM("DevOps monitoring expert",
                str(p, "task", "request"), str(p, "relevant_context", "context"),
                asStringList(p.get("focus")), asStringList(p.get("ignore")), asStringList(p.get("constraints")),
                "devops", msg.streamCallback);
            this.lastExecutedTime = System.currentTimeMillis() - start;
            return handleEmptyResponse(r, "DevOps");
        }
        private String str(Map<String, Object> p, String k1, String k2) {
            return String.valueOf(p.getOrDefault(k1, p.getOrDefault(k2, "")));
        }
    }

    public static class QAAgent extends SpecializedAgent {
        public QAAgent(AgentLLMBridge llmBridge) {
            super(llmBridge);
            this.name   = "QA";
            this.skills = Arrays.asList("unit_testing", "integration_testing", "debugging",
                "code_review", "regression_testing", "performance_testing",
                "security_testing", "compatibility_testing",
                "web_research", "api_integration", "fetch_external_content");
        }
        @Override public Object process(AgentMessage msg) {
            long start = System.currentTimeMillis();
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) msg.payload;
            Map<String, Object> r = invokeScopedLLM("QA testing expert",
                str(p, "task", "request"), str(p, "relevant_context", "context"),
                asStringList(p.get("focus")), asStringList(p.get("ignore")), asStringList(p.get("constraints")),
                "qa", msg.streamCallback);
            this.lastExecutedTime = System.currentTimeMillis() - start;
            return handleEmptyResponse(r, "QA");
        }
        private String str(Map<String, Object> p, String k1, String k2) {
            return String.valueOf(p.getOrDefault(k1, p.getOrDefault(k2, "")));
        }
    }

    // -------------------------------------------------------------------------
    // KnowledgeAgent -- searches MemoryService before calling LLM
    // -------------------------------------------------------------------------

    public static class KnowledgeAgent extends SpecializedAgent {
        private final MemoryService memoryService;

        public KnowledgeAgent(AgentLLMBridge llmBridge, MemoryService memoryService) {
            super(llmBridge);
            this.name          = "Knowledge";
            this.memoryService = memoryService;
            this.skills        = Arrays.asList("search_memory", "search_emails",
                "search_documents", "search_notes", "search_calendar", "search_contacts",
                "web_research", "api_integration", "fetch_external_content", "tavily_search");
        }

        @Override
        public Object process(AgentMessage msg) {
            long start = System.currentTimeMillis();
            @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) msg.payload;
            String task    = String.valueOf(p.getOrDefault("task",             p.getOrDefault("request",          "")));
            String context = String.valueOf(p.getOrDefault("relevant_context", p.getOrDefault("context",          "")));
            List<String> focus       = asStringList(p.get("focus"));
            List<String> ignore      = asStringList(p.get("ignore"));
            List<String> constraints = asStringList(p.get("constraints"));

            // Prepend retrieved memory chunks as RAG context
            String enrichedContext = context;
            if (memoryService != null) {
                try {
                    MemorySearchRequest req = new MemorySearchRequest();
                    req.setQuery(task);
                    req.setLimit(8);
                    List<MemoryEntry> hits = memoryService.search(req);
                    if (!hits.isEmpty()) {
                        StringBuilder sb = new StringBuilder("--- Retrieved from memory ---\n");
                        for (MemoryEntry e : hits) sb.append(e.getText()).append("\n---\n");
                        enrichedContext = sb + "\n" + context;
                    }
                } catch (Exception e) {
                    logger.warn("[KnowledgeAgent] Memory search failed: {}", e.getMessage());
                }
            }

            Map<String, Object> result = invokeScopedLLM(
                "personal knowledge assistant with access to the user's indexed emails, documents, notes, calendar, and real-time web search via Tavily",
                task, enrichedContext, focus, ignore, constraints, "knowledge", msg.streamCallback);
            this.lastExecutedTime = System.currentTimeMillis() - start;
            return handleEmptyResponse(result, "Knowledge");
        }
    }

    // -------------------------------------------------------------------------
    // AgentMessage
    // -------------------------------------------------------------------------

    public static class AgentMessage {
        public String targetAgent;
        public String type;
        public Object payload;
        public String replyTo;
        public StreamingCoordinatorCallback streamCallback;
        public long timestamp = System.currentTimeMillis();

        public AgentMessage(String targetAgent, String type, Object payload) {
            this(targetAgent, type, payload, (String) null, null);
        }
        public AgentMessage(String targetAgent, String type, Object payload, String replyTo) {
            this(targetAgent, type, payload, replyTo, null);
        }
        public AgentMessage(String targetAgent, String type, Object payload,
                            StreamingCoordinatorCallback streamCallback) {
            this(targetAgent, type, payload, null, streamCallback);
        }
        public AgentMessage(String targetAgent, String type, Object payload,
                            String replyTo, StreamingCoordinatorCallback streamCallback) {
            this.targetAgent    = targetAgent;
            this.type           = type;
            this.payload        = payload;
            this.replyTo        = replyTo;
            this.streamCallback = streamCallback;
        }
    }
}
