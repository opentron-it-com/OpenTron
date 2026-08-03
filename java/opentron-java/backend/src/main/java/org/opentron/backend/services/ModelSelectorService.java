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
 * STRATEGY: CLOUD FIRST (when API keys available) - don't wait for Ollama to crash
 * Local only as fallback when no cloud keys
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

    public ModelSelectorService(OllamaCliService ollamaService, CloudModelService cloudModelService) {
        this.ollamaService = ollamaService;
        this.cloudModelService = cloudModelService;
        loadAvailableModels();
    }

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
     * Backend specialist: Java, Spring, databases, APIs, thread-safety
     * PRIORITY: Cloud FIRST (Gemini) → Local models
     */
    private String selectBackendModel() {
        // CLOUD FIRST - Gemini Flash for fast, quality code analysis
        if (hasModel("gemini-3.6-flash")) {
            logger.info("Backend → gemini-3.6-flash (Google Cloud - PRIORITY)");
            return "gemini-3.6-flash";
        }
        if (hasModel("gemini-2.5-flash")) {
            logger.info("Backend → gemini-2.5-flash (Google Cloud - PRIORITY)");
            return "gemini-2.5-flash";
        }
        if (hasModel("claude-opus-4-6")) {
            logger.info("Backend → claude-opus-4-6 (Anthropic Cloud - PRIORITY)");
            return "claude-opus-4-6";
        }
        if (hasModel("claude-sonnet-4-6")) {
            logger.info("Backend → claude-sonnet-4-6 (Anthropic Cloud - PRIORITY)");
            return "claude-sonnet-4-6";
        }
        
        // LOCAL FALLBACK
        if (hasModel("deepseek-coder:6.7b")) {
            logger.info("Backend → deepseek-coder:6.7b (Local fallback)");
            return "deepseek-coder:6.7b";
        }
        if (hasModel("qwen2.5-coder:7b")) {
            logger.info("Backend → qwen2.5-coder:7b (Local fallback)");
            return "qwen2.5-coder:7b";
        }
        logger.info("Backend → llama3.2:3b (Last resort)");
        return "llama3.2:3b";
    }

    /**
     * Frontend specialist: React, TypeScript, components, UI
     * PRIORITY: Cloud FIRST → Local models
     */
    private String selectFrontendModel() {
        // CLOUD FIRST - Gemini Flash for fast UI/component analysis
        if (hasModel("gemini-3.6-flash")) {
            logger.info("Frontend → gemini-3.6-flash (Google Cloud - PRIORITY)");
            return "gemini-3.6-flash";
        }
        if (hasModel("claude-sonnet-4-6")) {
            logger.info("Frontend → claude-sonnet-4-6 (Anthropic Cloud - PRIORITY)");
            return "claude-sonnet-4-6";
        }
        
        // LOCAL FALLBACK
        if (hasModel("qwen2.5-coder:7b")) {
            logger.info("Frontend → qwen2.5-coder:7b (Local fallback)");
            return "qwen2.5-coder:7b";
        }
        if (hasModel("deepseek-coder:6.7b")) {
            logger.info("Frontend → deepseek-coder:6.7b (Local fallback)");
            return "deepseek-coder:6.7b";
        }
        logger.info("Frontend → llama3.2:3b (Last resort)");
        return "llama3.2:3b";
    }

    /**
     * QA specialist: testing, debugging, code review, test generation
     * PRIORITY: Cloud FIRST → Local models
     */
    private String selectQAModel() {
        // CLOUD FIRST - Gemini Flash for fast test generation and debugging
        if (hasModel("gemini-3.6-flash")) {
            logger.info("QA → gemini-3.6-flash (Google Cloud - PRIORITY)");
            return "gemini-3.6-flash";
        }
        if (hasModel("claude-sonnet-4-6")) {
            logger.info("QA → claude-sonnet-4-6 (Anthropic Cloud - PRIORITY)");
            return "claude-sonnet-4-6";
        }
        
        // LOCAL FALLBACK
        if (hasModel("qwen2.5-coder:7b")) {
            logger.info("QA → qwen2.5-coder:7b (Local fallback)");
            return "qwen2.5-coder:7b";
        }
        if (hasModel("deepseek-coder:6.7b")) {
            logger.info("QA → deepseek-coder:6.7b (Local fallback)");
            return "deepseek-coder:6.7b";
        }
        logger.info("QA → llama3.2:3b (Last resort)");
        return "llama3.2:3b";
    }

    /**
     * DevOps specialist: infrastructure, Docker, Kubernetes, monitoring, configuration
     * PRIORITY: Cloud FIRST → Local models
     */
    private String selectDevOpsModel() {
        // CLOUD FIRST - Gemini Flash for fast infrastructure analysis
        if (hasModel("gemini-3.6-flash")) {
            logger.info("DevOps → gemini-3.6-flash (Google Cloud - PRIORITY)");
            return "gemini-3.6-flash";
        }
        if (hasModel("claude-sonnet-4-6")) {
            logger.info("DevOps → claude-sonnet-4-6 (Anthropic Cloud - PRIORITY)");
            return "claude-sonnet-4-6";
        }
        
        // LOCAL FALLBACK
        if (hasModel("qwen2.5-coder:7b")) {
            logger.info("DevOps → qwen2.5-coder:7b (Local fallback)");
            return "qwen2.5-coder:7b";
        }
        if (hasModel("deepseek-coder:6.7b")) {
            logger.info("DevOps → deepseek-coder:6.7b (Local fallback)");
            return "deepseek-coder:6.7b";
        }
        logger.info("DevOps → llama3.2:3b (Last resort)");
        return "llama3.2:3b";
    }

    /**
     * Knowledge specialist: general Q&A, reasoning, explanations
     * PRIORITY: Claude Haiku (fast, cheap) → Gemini → Local models
     */
    private String selectKnowledgeModel() {
        // CLOUD FIRST - Claude Haiku for speed and cost
        if (hasModel("claude-haiku-4-5")) {
            logger.info("Knowledge → claude-haiku-4-5 (Anthropic Haiku - PRIORITY)");
            return "claude-haiku-4-5";
        }
        if (hasModel("gemini-3.6-flash")) {
            logger.info("Knowledge → gemini-3.6-flash (Google Cloud - PRIORITY)");
            return "gemini-3.6-flash";
        }
        
        // LOCAL FALLBACK
        if (hasModel("llama3.2:3b")) {
            logger.info("Knowledge → llama3.2:3b (Local fallback)");
            return "llama3.2:3b";
        }
        if (hasModel("qwen2.5-coder:7b")) {
            logger.info("Knowledge → qwen2.5-coder:7b (Local fallback)");
            return "qwen2.5-coder:7b";
        }
        logger.info("Knowledge → llama3.2:1b (Last resort)");
        return "llama3.2:1b";
    }

    private String selectGeneralModel() {
        // Cloud first, then local
        if (hasModel("gemini-3.6-flash")) return "gemini-3.6-flash";
        if (hasModel("claude-sonnet-4-6")) return "claude-sonnet-4-6";
        if (hasModel("llama3.2:1b")) return "llama3.2:1b";
        if (hasModel("qwen2.5-coder:7b")) return "qwen2.5-coder:7b";
        if (hasModel("llama3.2:3b")) return "llama3.2:3b";
        if (hasModel("deepseek-coder:6.7b")) return "deepseek-coder:6.7b";
        return "llama3.2:1b";
    }

    private boolean hasModel(String modelName) {
        return modelCache.containsKey(modelName);
    }

    private void loadAvailableModels() {
        loadAvailableModels(null);
    }

    private void loadAvailableModels(Map<String, String> apiKeyOverrides) {
        logger.info("Loading available models with overrides={}", apiKeyOverrides);
        try {
            modelCache.clear();

            // Load local models
            List<String> localModels = ollamaService.listModels().block();
            if (localModels != null) {
                for (String model : localModels) {
                    if (model != null && !model.isBlank()) {
                        modelCache.put(model.trim(), "available");
                    }
                }
            }

            // Load cloud models
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

            if (cloudModels != null) {
                for (String model : cloudModels) {
                    if (model != null && !model.isBlank()) {
                        modelCache.put(model.trim(), "available");
                    }
                }
            }

            addKnownCloudCandidates(apiKeyOverrides);

            lastCacheUpdate = System.currentTimeMillis();
            lastApiKeyOverrides = apiKeyOverrides == null ? null : new HashMap<>(apiKeyOverrides);
            logger.info("Loaded {} models: {}", modelCache.size(), modelCache.keySet());
        } catch (Exception e) {
            logger.error("Error loading models", e);
            modelCache.clear();
            modelCache.put("llama3.2:1b", "available");
        }
    }

    private void addKnownCloudCandidates(Map<String, String> apiKeyOverrides) {
        boolean hasAnthropic = cloudModelService.hasApiKey("anthropic", apiKeyOverrides);
        boolean hasGoogle = cloudModelService.hasApiKey("google", apiKeyOverrides);
        boolean hasOpenRouter = cloudModelService.hasApiKey("openrouter", apiKeyOverrides);

        // Google models - add if key present, remove if absent
        if (hasGoogle) {
            modelCache.putIfAbsent("gemini-3.6-flash", "available");
            modelCache.putIfAbsent("gemini-3.1-pro", "available");
            modelCache.putIfAbsent("gemini-2.5-flash", "available");
        } else {
            modelCache.remove("gemini-3.6-flash");
            modelCache.remove("gemini-3.1-pro");
            modelCache.remove("gemini-2.5-flash");
        }

        // Anthropic models - add if key present, remove if absent
        if (hasAnthropic) {
            modelCache.putIfAbsent("claude-opus-4-6", "available");
            modelCache.putIfAbsent("claude-sonnet-4-6", "available");
            modelCache.putIfAbsent("claude-haiku-4-5", "available");
        } else {
            modelCache.remove("claude-opus-4-6");
            modelCache.remove("claude-sonnet-4-6");
            modelCache.remove("claude-haiku-4-5");
        }

        // OpenRouter models - add if key present, remove if absent
        if (hasOpenRouter) {
            modelCache.putIfAbsent("openrouter/auto", "available");
        } else {
            modelCache.remove("openrouter/auto");
        }
    }

    private void refreshModelCacheIfNeeded(Map<String, String> apiKeyOverrides) {
        long now = System.currentTimeMillis();
        boolean overridesChanged = !Objects.equals(lastApiKeyOverrides, apiKeyOverrides == null ? null : new HashMap<>(apiKeyOverrides));
        if (overridesChanged || now - lastCacheUpdate > CACHE_TTL_MS) {
            logger.debug("Refreshing models because cache expired or API-key overrides changed");
            loadAvailableModels(apiKeyOverrides);
        }
    }

    public Map<String, Object> getModelSelectorStatus() {
        refreshModelCacheIfNeeded(null);
        return Map.of(
            "available_models", modelCache.keySet(),
            "model_count", modelCache.size(),
            "backend_model", selectBackendModel(),
            "frontend_model", selectFrontendModel(),
            "qa_model", selectQAModel(),
            "devops_model", selectDevOpsModel(),
            "knowledge_model", selectKnowledgeModel()
        );
    }
}
