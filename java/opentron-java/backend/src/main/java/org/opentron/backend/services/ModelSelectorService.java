package org.opentron.backend.services;

import org.springframework.stereotype.Service;
import org.opentron.backend.util.CloudModelService;
import org.opentron.backend.util.OllamaCliService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ModelSelectorService - Intelligently selects the best model for each task
 * 
 * Maps agent specialization → optimal model from available pool
 * Maximizes speed and quality by choosing purpose-built models
 */
@Service
public class ModelSelectorService {
    
    private final OllamaCliService ollamaService;
    private final CloudModelService cloudModelService;
    private static final Logger logger = LoggerFactory.getLogger(ModelSelectorService.class);
    private final Map<String, String> modelCache = new ConcurrentHashMap<>();
    private long lastCacheUpdate = 0;
    private Map<String, String> lastApiKeyOverrides = null;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private List<String> getPreferredCloudModels() {
        return List.of(
            "claude-haiku-4-5",
            "claude-opus-4-6",
            "claude-sonnet-4-6",
            "openrouter/anthropic/claude-sonnet-4",
            "openrouter/auto",
            "openrouter/deepseek/deepseek-r1",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-3-pro"
        );
    }

    public ModelSelectorService(OllamaCliService ollamaService, CloudModelService cloudModelService) {
        this.ollamaService = ollamaService;
        this.cloudModelService = cloudModelService;
        loadAvailableModels();
    }

    /**
     * Select the best available model for a given agent type
     */
    public String selectBestModel(String agentType) {
        return selectBestModel(agentType, null);
    }

    public String selectBestModel(String agentType, Map<String, String> apiKeyOverrides) {
        refreshModelCacheIfNeeded(apiKeyOverrides);
        
        logger.info("Selecting model for: {}", agentType);
        
        switch (agentType.toLowerCase()) {
            case "backend":
                return selectBackendModel();
            case "frontend":
                return selectFrontendModel();
            case "qa":
                return selectQAModel();
            case "devops":
                return selectDevOpsModel();
            case "knowledge":
                return selectKnowledgeModel();
            default:
                return selectGeneralModel();
        }
    }

    /**
     * Backend specialist: optimize for code, caching, database queries, API design
     * Prefers: neural-chat (code-optimized) > qwen3.5:0.8b > mistral
     */
    private String selectBackendModel() {
        String cloudModel = selectCloudModel(getPreferredCloudModels());
        if (cloudModel != null) {
            logger.info("Backend → {} (cloud model)", cloudModel);
            return cloudModel;
        }
        if (hasModel("neural-chat")) {
            logger.info("Backend → neural-chat (code-optimized)");
            return "neural-chat";
        }
        if (hasModel("qwen3.5:0.8b")) {
            logger.info("Backend → qwen3.5:0.8b (fallback)");
            return "qwen3.5:0.8b";
        }
        logger.info("Backend → mistral (last resort)");
        return "mistral";
    }

    /**
     * Frontend specialist: optimize for React, TypeScript, components, state management
     * Prefers: neural-chat (code-optimized) > qwen3:8b > qwen3.5:9b > mistral
     */
    private String selectFrontendModel() {
        String cloudModel = selectCloudModel(getPreferredCloudModels());
        if (cloudModel != null) {
            logger.info("Frontend → {} (cloud model)", cloudModel);
            return cloudModel;
        }
        if (hasModel("neural-chat")) {
            logger.info("Frontend → neural-chat (React expert)");
            return "neural-chat";
        }
        if (hasModel("qwen3:8b")) {
            logger.info("Frontend → qwen3:8b (fast, capable)");
            return "qwen3:8b";
        }
        if (hasModel("qwen3.5:9b")) {
            logger.info("Frontend → qwen3.5:9b (fast, capable)");
            return "qwen3.5:9b";
        }
        logger.info("Frontend → mistral (last resort)");
        return "mistral";
    }

    /**
     * QA specialist: optimize for testing, debugging, code review
     * Prefers: neural-chat (code review expert) > qwen3.5:2b (very fast) > mistral
     */
    private String selectQAModel() {
        String cloudModel = selectCloudModel(getPreferredCloudModels());
        if (cloudModel != null) {
            logger.info("QA → {} (cloud model)", cloudModel);
            return cloudModel;
        }
        if (hasModel("neural-chat")) {
            logger.info("QA → neural-chat (code review)");
            return "neural-chat";
        }
        if (hasModel("qwen3.5:2b")) {
            logger.info("QA → qwen3.5:2b (very fast)");
            return "qwen3.5:2b";
        }
        logger.info("QA → mistral (last resort)");
        return "mistral";
    }

    /**
     * DevOps specialist: optimize for monitoring, metrics, infrastructure
     * Prefers: qwen3.5:0.8b (larger, complex configs) > neural-chat > mistral
     */
    private String selectDevOpsModel() {
        String cloudModel = selectCloudModel(getPreferredCloudModels());
        if (cloudModel != null) {
            logger.info("DevOps → {} (cloud model)", cloudModel);
            return cloudModel;
        }
        if (hasModel("qwen3.5:0.8b")) {
            logger.info("DevOps → qwen3.5:0.8b (infrastructure)");
            return "qwen3.5:0.8b";
        }
        if (hasModel("neural-chat")) {
            logger.info("DevOps → neural-chat (fallback)");
            return "neural-chat";
        }
        logger.info("DevOps → mistral (last resort)");
        return "mistral";
    }

    /**
     * Knowledge specialist: optimize for memory-backed reasoning and general assistance.
     * Prefers cloud models first when available, then general-purpose local models.
     */
    private String selectKnowledgeModel() {
        String cloudModel = selectCloudModel(getPreferredCloudModels());
        if (cloudModel != null) {
            logger.info("Knowledge → {} (cloud model)", cloudModel);
            return cloudModel;
        }
        if (hasModel("neural-chat")) {
            logger.info("Knowledge → neural-chat (general reasoning)");
            return "neural-chat";
        }
        if (hasModel("qwen3:8b")) {
            logger.info("Knowledge → qwen3:8b (fast, capable)");
            return "qwen3:8b";
        }
        if (hasModel("qwen3.5:9b")) {
            logger.info("Knowledge → qwen3.5:9b (fast, capable)");
            return "qwen3.5:9b";
        }
        if (hasModel("qwen3.5:2b")) {
            logger.info("Knowledge → qwen3.5:2b (very fast)");
            return "qwen3.5:2b";
        }
        logger.info("Knowledge → mistral (last resort)");
        return "mistral";
    }

    /**
     * General purpose: select fastest available
     * Prefers: neural-chat > qwen3:8b > qwen3.5:9b > qwen3.5:2b > mistral
     */
    private String selectGeneralModel() {
        String cloudModel = selectCloudModel(getPreferredCloudModels());
        if (cloudModel != null) {
            logger.info("General → {} (cloud model)", cloudModel);
            return cloudModel;
        }
        if (hasModel("neural-chat")) return "neural-chat";
        if (hasModel("qwen3:8b")) return "qwen3:8b";
        if (hasModel("qwen3.5:9b")) return "qwen3.5:9b";
        if (hasModel("qwen3.5:2b")) return "qwen3.5:2b";
        return "mistral";
    }

    /**
     * Check if a model is available
     */
    private boolean hasModel(String modelName) {
        return modelCache.containsKey(modelName);
    }

    private String selectCloudModel(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        // Prefer exact matches first
        for (String candidate : candidates) {
            if (hasModel(candidate)) return candidate;
        }

        // Fall back to fuzzy/normalized matching to tolerate provider naming differences
        Set<String> available = modelCache.keySet();
        for (String candidate : candidates) {
            String normCand = normalizeName(candidate);
            for (String avail : available) {
                String normAvail = normalizeName(avail);
                if (normAvail.contains(normCand) || normCand.contains(normAvail)) {
                    logger.debug("Fuzzy matched cloud candidate '{}' -> available '{}'", candidate, avail);
                    return avail;
                }
            }
        }

        return null;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        return s.replaceAll("[^a-z0-9]", "").toLowerCase();
    }

    /**
     * Load available models from Ollama and cloud providers.
     */
    private void loadAvailableModels() {
        loadAvailableModels(null);
    }

    private void loadAvailableModels(Map<String, String> apiKeyOverrides) {
        logger.info("Loading available models with overrides={}", apiKeyOverrides);
        try {
            modelCache.clear();

            List<String> localModels = ollamaService.listModels().block();
            if (localModels != null) {
                for (String model : localModels) {
                    if (model != null && !model.isBlank()) {
                        modelCache.put(model.trim(), "available");
                    }
                }
            }

            List<String> cloudModels = null;
            try {
                if (cloudModelService != null) {
                    var cloudModelsMono = cloudModelService.listModels(apiKeyOverrides);
                    if (cloudModelsMono != null) {
                        cloudModels = cloudModelsMono.block();
                    }
                }
            } catch (Exception e) {
                logger.warn("Unable to load cloud models with overrides {}", apiKeyOverrides, e);
            }

            if (cloudModels == null) {
                try {
                    if (cloudModelService != null) {
                        var fallbackMono = cloudModelService.listModels();
                        if (fallbackMono != null) {
                            cloudModels = fallbackMono.block();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Unable to load cloud models with fallback", e);
                }
            }

            if (cloudModels != null) {
                for (String model : cloudModels) {
                    if (model != null && !model.isBlank()) {
                        modelCache.put(model.trim(), "available");
                    }
                }
            }

            addKnownCloudCandidates(apiKeyOverrides);

            if (modelCache.isEmpty()) {
                modelCache.put("neural-chat", "available");
                modelCache.put("qwen3.5:0.8b", "available");
                modelCache.put("qwen3.5:9b", "available");
                modelCache.put("qwen3.5:2b", "available");
                modelCache.put("mistral", "available");
                modelCache.put("llama2", "available");
                modelCache.put("llava", "available");
            }

            lastCacheUpdate = System.currentTimeMillis();
            lastApiKeyOverrides = apiKeyOverrides == null ? null : new HashMap<>(apiKeyOverrides);
            logger.info("Loaded {} models: {}", modelCache.size(), modelCache.keySet());
        } catch (Exception e) {
            logger.error("Error loading models", e);
            modelCache.clear();
            modelCache.put("mistral", "available");
        }
    }

    private void addKnownCloudCandidates(Map<String, String> apiKeyOverrides) {
        boolean hasAnthropic = cloudModelService.hasApiKey("anthropic", apiKeyOverrides);
        boolean hasOpenRouter = cloudModelService.hasApiKey("openrouter", apiKeyOverrides);
        boolean hasGoogle = cloudModelService.hasApiKey("google", apiKeyOverrides);

        if (hasOpenRouter) {
            modelCache.putIfAbsent("openrouter/auto", "available");
            modelCache.putIfAbsent("openrouter/deepseek/deepseek-r1", "available");
            modelCache.putIfAbsent("openrouter/anthropic/claude-sonnet-4.6", "available");
        }
        if (hasGoogle) {
            modelCache.putIfAbsent("gemini-2.5-pro", "available");
            modelCache.putIfAbsent("gemini-2.5-flash", "available");
            modelCache.putIfAbsent("gemini-3-pro", "available");
        }
        if (hasAnthropic) {
            modelCache.putIfAbsent("claude-opus-4-6", "available");
            modelCache.putIfAbsent("claude-sonnet-4-6", "available");
            modelCache.putIfAbsent("claude-haiku-4-5", "available");
        }
    }

    /**
     * Refresh model cache if TTL expired
     */
    private void refreshModelCacheIfNeeded() {
        refreshModelCacheIfNeeded(null);
    }

    private void refreshModelCacheIfNeeded(Map<String, String> apiKeyOverrides) {
        long now = System.currentTimeMillis();
        boolean overridesChanged = !Objects.equals(lastApiKeyOverrides, apiKeyOverrides == null ? null : new HashMap<>(apiKeyOverrides));
        if (overridesChanged || now - lastCacheUpdate > CACHE_TTL_MS) {
            logger.debug("Refreshing models because cache expired or API-key overrides changed");
            loadAvailableModels(apiKeyOverrides);
        }
    }

    /**
     * Get model information for debugging
     */
    public Map<String, Object> getModelSelectorStatus() {
        refreshModelCacheIfNeeded();
        return Map.of(
            "available_models", modelCache.keySet(),
            "model_count", modelCache.size(),
            "last_cache_update", lastCacheUpdate,
            "cache_ttl_ms", CACHE_TTL_MS,
            "backend_model", selectBackendModel(),
            "frontend_model", selectFrontendModel(),
            "qa_model", selectQAModel(),
            "devops_model", selectDevOpsModel(),
            "knowledge_model", selectKnowledgeModel()
        );
    }
}
