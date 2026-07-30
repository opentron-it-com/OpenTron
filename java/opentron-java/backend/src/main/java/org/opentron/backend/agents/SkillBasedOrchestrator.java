package org.opentron.backend.agents;

import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Skill-Based Orchestrator using OpenClaw principles
 * 
 * Routes tasks to agents based on skill matching:
 * - Analyzes task requirements
 * - Queries database for skill-matching agents
 * - Selects 1-2 best agents per domain
 * - Orchestrates parallel execution
 * - Aggregates domain-specific results
 * 
 * Skill domains:
 * - CODE: java_optimization, spring_boot_configuration, backend_arch
 * - FRONTEND: react_optimization, component_design, ui_performance
 * - QA: unit_testing, integration_testing, code_review, debugging
 * - DEVOPS: performance_monitoring, metrics_collection, health_checks
 * - WEBDESIGN: css_optimization, responsive_design, accessibility
 */
@Service
public class SkillBasedOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SkillBasedOrchestrator.class);

    private final MultiAgentCoordinator coordinator;
    private final SkillRegistry skillRegistry;

    public SkillBasedOrchestrator(MultiAgentCoordinator coordinator) {
        this.coordinator = coordinator;
        this.skillRegistry = new SkillRegistry();
    }

    /**
     * Main orchestration entry point
     * Analyzes task and routes to skill-matched agents
     */
    public Map<String, Object> orchestrateTask(String taskDescription, String context) {
        long start = System.currentTimeMillis();

        logger.info("Analyzing task: {}", taskDescription);

        // 1. Extract required skills from task description
        List<String> requiredSkills = extractSkills(taskDescription);
        logger.debug("Required skills: {}", requiredSkills);

        // 2. Group skills by domain
        Map<String, List<String>> skillsByDomain = groupSkillsByDomain(requiredSkills);
        logger.debug("Domains: {}", skillsByDomain.keySet());

        // 3. Select best agents for each domain (1-2 agents per domain)
        Map<String, List<AgentSkillMatch>> selectedAgents = selectAgentsForDomains(skillsByDomain);

        // 4. Orchestrate parallel execution
        Map<String, Object> results = executeParallel(selectedAgents, taskDescription, context);

        long elapsed = System.currentTimeMillis() - start;
        results.put("elapsed_ms", elapsed);
        results.put("required_skills", requiredSkills);
        results.put("domains", skillsByDomain.keySet());

        logger.info("Task completed in {}ms", elapsed);
        return results;
    }

    /**
     * Extract skill keywords from task description
     */
    private List<String> extractSkills(String task) {
        List<String> skills = new ArrayList<>();
        String lower = task.toLowerCase();

        // CODE skills
        if (lower.contains("java") || lower.contains("spring") || lower.contains("backend") ||
            lower.contains("cache") || lower.contains("optimize") || lower.contains("performance")) {
            skills.add("java_optimization");
            skills.add("spring_boot_configuration");
            skills.add("database_query_optimization");
            skills.add("cache_optimization");
        }

        // FRONTEND skills
        if (lower.contains("react") || lower.contains("frontend") || lower.contains("component") ||
            lower.contains("ui") || lower.contains("css") || lower.contains("responsive")) {
            skills.add("react_optimization");
            skills.add("component_design");
            skills.add("css_optimization");
            skills.add("responsive_design");
        }

        // WEBDESIGN skills
        if (lower.contains("design") || lower.contains("layout") || lower.contains("accessibility") ||
            lower.contains("ux") || lower.contains("a11y")) {
            skills.add("accessibility");
            skills.add("responsive_design");
            skills.add("ui_design");
        }

        // QA skills
        if (lower.contains("test") || lower.contains("debug") || lower.contains("review") ||
            lower.contains("bug") || lower.contains("qa")) {
            skills.add("unit_testing");
            skills.add("integration_testing");
            skills.add("debugging");
            skills.add("code_review");
        }

        // DEVOPS skills
        if (lower.contains("monitor") || lower.contains("metric") || lower.contains("health") ||
            lower.contains("performance") || lower.contains("alert")) {
            skills.add("performance_monitoring");
            skills.add("metrics_collection");
            skills.add("health_checks");
        }

        // KNOWLEDGE skills
        if (lower.contains("find") || lower.contains("search") || lower.contains("what") ||
            lower.contains("who") || lower.contains("when") || lower.contains("tell me") ||
            lower.contains("summarize") || lower.contains("recall") || lower.contains("email") ||
            lower.contains("meeting") || lower.contains("document") || lower.contains("note") ||
            lower.contains("slack") || lower.contains("calendar") || lower.contains("contact")) {
            skills.add("search_memory");
            skills.add("search_documents");
            skills.add("search_emails");
        }

        // Internet-enabled skills (available across all domains)
        if (lower.contains("fetch")|| lower.contains("search") || lower.contains("download") || lower.contains("api") ||
            lower.contains("external") || lower.contains("internet") || lower.contains("web") ||
            lower.contains("browse") || lower.contains("research") || lower.contains("look up")) {
            skills.add("web_research");
            skills.add("api_integration");
            skills.add("fetch_external_content");
        }

        // Default: code + frontend
        if (skills.isEmpty()) {
            skills.add("java_optimization");
            skills.add("react_optimization");
        }

        return skills;
    }

    /**
     * Group skills by domain (CODE, FRONTEND, QA, DEVOPS, WEBDESIGN)
     */
    private Map<String, List<String>> groupSkillsByDomain(List<String> skills) {
        Map<String, List<String>> domains = new LinkedHashMap<>();

        for (String skill : skills) {
            String domain = skillRegistry.getDomain(skill);
            domains.computeIfAbsent(domain, k -> new ArrayList<>()).add(skill);
        }

        return domains;
    }

    /**
     * Select best agents for each domain (1-2 agents per domain)
     */
    private Map<String, List<AgentSkillMatch>> selectAgentsForDomains(
            Map<String, List<String>> skillsByDomain) {

        Map<String, List<AgentSkillMatch>> selectedAgents = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : skillsByDomain.entrySet()) {
            String domain = entry.getKey();
            List<String> domainSkills = entry.getValue();

            // Get agents for this domain
            List<AgentSkillMatch> matchedAgents = getAgentsForDomain(domain, domainSkills);

            // Select top 1-2 agents by match score
            List<AgentSkillMatch> topAgents = matchedAgents.stream()
                    .sorted((a, b) -> Double.compare(b.matchScore, a.matchScore))
                    .limit(2)
                    .collect(Collectors.toList());

            selectedAgents.put(domain, topAgents);

                logger.debug("Domain: {} -> Selected agents: {}", domain,
                    topAgents.stream().map(a -> a.agentName + "(" + a.matchScore + ")")
                        .collect(Collectors.joining(", ")));
        }

        return selectedAgents;
    }

    /**
     * Get agents matching a specific domain
     */
    private List<AgentSkillMatch> getAgentsForDomain(String domain, List<String> requiredSkills) {
        List<AgentSkillMatch> matches = new ArrayList<>();

        // Map domains to agents
        switch (domain) {
            case "CODE":
                matches.add(new AgentSkillMatch("backend", 0.95, requiredSkills));
                break;
            case "FRONTEND":
                matches.add(new AgentSkillMatch("frontend", 0.95, requiredSkills));
                break;
            case "WEBDESIGN":
                matches.add(new AgentSkillMatch("frontend", 0.80, requiredSkills));
                break;
            case "QA":
                matches.add(new AgentSkillMatch("qa", 0.95, requiredSkills));
                break;
            case "DEVOPS":
                matches.add(new AgentSkillMatch("devops", 0.95, requiredSkills));
                break;
            case "KNOWLEDGE":
                matches.add(new AgentSkillMatch("knowledge", 0.95, requiredSkills));
                break;
            default:
                // Generic fallback
                matches.add(new AgentSkillMatch("backend", 0.60, requiredSkills));
                matches.add(new AgentSkillMatch("frontend", 0.60, requiredSkills));
        }

        return matches;
    }

    /**
     * Execute agents in parallel and aggregate results
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeParallel(Map<String, List<AgentSkillMatch>> selectedAgents,
                                                 String taskDescription, String context) {
        Map<String, Object> results = new LinkedHashMap<>();
        List<String> agentsUsed = new ArrayList<>();

        for (Map.Entry<String, List<AgentSkillMatch>> entry : selectedAgents.entrySet()) {
            String domain = entry.getKey();
            List<AgentSkillMatch> agents = entry.getValue();

            List<Map<String, Object>> domainResults = new ArrayList<>();

            for (AgentSkillMatch agentMatch : agents) {
                String agentName = agentMatch.agentName;
                agentsUsed.add(agentName + "(" + domain + ")");

                try {
                    // Create agent-specific task
                    Map<String, Object> agentTask = new LinkedHashMap<>();
                    agentTask.put("request", taskDescription);
                    agentTask.put("context", context);
                    agentTask.put("domain", domain);
                    agentTask.put("required_skills", agentMatch.matchedSkills);
                    agentTask.put("match_score", agentMatch.matchScore);

                    // Invoke agent via coordinator
                    MultiAgentCoordinator.AgentMessage msg = new MultiAgentCoordinator.AgentMessage(
                            agentName, "task", agentTask);

                    MultiAgentCoordinator.SpecializedAgent agent = coordinator.getAgent(agentName);
                    if (agent != null) {
                        Object agentResult = agent.process(msg);
                        domainResults.add((Map<String, Object>) agentResult);
                    }
                } catch (Exception e) {
                    logger.error("Error executing {}", agentName, e);
                    domainResults.add(Map.of(
                            "agent", agentName,
                            "error", e.getMessage(),
                            "status", "failed"
                    ));
                }
            }

            results.put(domain.toLowerCase(), domainResults);
        }

        results.put("agents_used", agentsUsed);
        results.put("status", "completed");

        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agent Skill Match
    // ─────────────────────────────────────────────────────────────────────────

    public static class AgentSkillMatch {
        public String agentName;
        public double matchScore; // 0.0 - 1.0
        public List<String> matchedSkills;

        public AgentSkillMatch(String agentName, double matchScore, List<String> matchedSkills) {
            this.agentName = agentName;
            this.matchScore = matchScore;
            this.matchedSkills = matchedSkills;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Skill Registry
    // ─────────────────────────────────────────────────────────────────────────

    private static class SkillRegistry {
        private final Map<String, String> skillToDomain = new LinkedHashMap<>();

        public SkillRegistry() {
            // CODE domain
            skillToDomain.put("java_optimization", "CODE");
            skillToDomain.put("spring_boot_configuration", "CODE");
            skillToDomain.put("database_query_optimization", "CODE");
            skillToDomain.put("cache_optimization", "CODE");
            skillToDomain.put("api_design", "CODE");
            skillToDomain.put("persistence_layer", "CODE");
            skillToDomain.put("concurrent_programming", "CODE");
            // Internet-enabled skills for CODE domain
            skillToDomain.put("web_research", "CODE");
            skillToDomain.put("api_integration", "CODE");
            skillToDomain.put("fetch_external_content", "CODE");

            // FRONTEND domain
            skillToDomain.put("react_optimization", "FRONTEND");
            skillToDomain.put("component_design", "FRONTEND");
            skillToDomain.put("state_management", "FRONTEND");
            skillToDomain.put("performance_tuning", "FRONTEND");
            skillToDomain.put("bundle_optimization", "FRONTEND");
            // Internet-enabled skills for FRONTEND domain
            skillToDomain.put("web_research", "FRONTEND");
            skillToDomain.put("api_integration", "FRONTEND");
            skillToDomain.put("fetch_external_content", "FRONTEND");

            // WEBDESIGN domain
            skillToDomain.put("css_optimization", "WEBDESIGN");
            skillToDomain.put("responsive_design", "WEBDESIGN");
            skillToDomain.put("accessibility", "WEBDESIGN");
            skillToDomain.put("ui_design", "WEBDESIGN");
            // Internet-enabled skills for WEBDESIGN domain
            skillToDomain.put("web_research", "WEBDESIGN");
            skillToDomain.put("api_integration", "WEBDESIGN");
            skillToDomain.put("fetch_external_content", "WEBDESIGN");

            // QA domain
            skillToDomain.put("unit_testing", "QA");
            skillToDomain.put("integration_testing", "QA");
            skillToDomain.put("debugging", "QA");
            skillToDomain.put("code_review", "QA");
            skillToDomain.put("regression_testing", "QA");
            skillToDomain.put("performance_testing", "QA");
            skillToDomain.put("security_testing", "QA");
            // Internet-enabled skills for QA domain
            skillToDomain.put("web_research", "QA");
            skillToDomain.put("api_integration", "QA");
            skillToDomain.put("fetch_external_content", "QA");

            // KNOWLEDGE domain
            skillToDomain.put("search_memory",    "KNOWLEDGE");
            skillToDomain.put("search_documents", "KNOWLEDGE");
            skillToDomain.put("search_emails",    "KNOWLEDGE");
            skillToDomain.put("search_notes",     "KNOWLEDGE");
            skillToDomain.put("search_calendar",  "KNOWLEDGE");
            skillToDomain.put("search_contacts",  "KNOWLEDGE");
            // Internet-enabled skills for KNOWLEDGE domain
            skillToDomain.put("web_research", "KNOWLEDGE");
            skillToDomain.put("api_integration", "KNOWLEDGE");
            skillToDomain.put("fetch_external_content", "KNOWLEDGE");

            // DEVOPS domain
            skillToDomain.put("performance_monitoring", "DEVOPS");
            skillToDomain.put("metrics_collection", "DEVOPS");
            skillToDomain.put("health_checks", "DEVOPS");
            skillToDomain.put("alerting", "DEVOPS");
            skillToDomain.put("log_aggregation", "DEVOPS");
            // Internet-enabled skills for DEVOPS domain
            skillToDomain.put("web_research", "DEVOPS");
            skillToDomain.put("api_integration", "DEVOPS");
            skillToDomain.put("fetch_external_content", "DEVOPS");
        }

        public String getDomain(String skill) {
            return skillToDomain.getOrDefault(skill, "CODE"); // Default to CODE
        }
    }


}

