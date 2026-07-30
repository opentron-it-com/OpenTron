package org.opentron.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EngineRouting {

    private static final Logger logger = LoggerFactory.getLogger(EngineRouting.class);

    public enum EngineType {
        AUTO,
        OLLAMA,
        OPENAI
    }

    /**
     * Probe the engine for available model tags and return the first model name, or null.
     */
    public Mono<String> pickFirstAvailableModel() {
        try {
            return webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(body -> {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode root = mapper.readTree(body);
                            // Ollama returns { "models": [ {"name":..., "model":...}, ... ] }
                            if (root.has("models") && root.get("models").isArray() && root.get("models").size() > 0) {
                                JsonNode first = root.get("models").get(0);
                                if (first.has("model")) return first.get("model").asText();
                                if (first.has("name")) return first.get("name").asText();
                            }
                            // also accept a plain array of strings or array of objects
                            if (root.isArray() && root.size() > 0) {
                                JsonNode first = root.get(0);
                                if (first.isTextual()) return first.asText();
                                if (first.has("name")) return first.get("name").asText();
                                if (first.has("tag")) return first.get("tag").asText();
                                if (first.has("model")) return first.get("model").asText();
                            }
                        } catch (Exception ignored) {
                        }
                        return null;
                    })
                    .onErrorReturn(null);
        } catch (Exception e) {
            return Mono.justOrEmpty((String) null);
        }
    }
    public Mono<Boolean> hasModel(String modelName) {
        if (modelName == null || modelName.isBlank()) return Mono.just(false);
        try {
            return webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(body -> {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode root = mapper.readTree(body);
                            if (root.has("models") && root.get("models").isArray()) {
                                for (JsonNode m : root.get("models")) {
                                    if (m.has("model") && modelName.equals(m.get("model").asText())) return true;
                                    if (m.has("name") && modelName.equals(m.get("name").asText())) return true;
                                }
                            }
                            if (root.isArray()) {
                                for (JsonNode m : root) {
                                    if (m.isTextual() && modelName.equals(m.asText())) return true;
                                    if (m.has("model") && modelName.equals(m.get("model").asText())) return true;
                                    if (m.has("name") && modelName.equals(m.get("name").asText())) return true;
                                }
                            }
                        } catch (Exception ignored) {}
                        return false;
                    }).onErrorReturn(false);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    private final WebClient webClient;
    private final String engineHost;
    private final EngineType configuredType;
    private volatile EngineType resolvedType;

    public EngineRouting(WebClient webClient,
                         @Value("${engine.host:http://localhost:11434}") String engineHost,
                         @Value("${engine.type:auto}") String engineType) {
        this.webClient = webClient;
        this.engineHost = engineHost == null ? "" : engineHost.trim().toLowerCase();
        this.configuredType = parseEngineType(engineType);
        this.resolvedType = this.configuredType == EngineType.AUTO ? null : this.configuredType;
    }

    private EngineType parseEngineType(String engineType) {
        if (engineType == null || engineType.isBlank()) {
            return EngineType.AUTO;
        }
        switch (engineType.trim().toLowerCase()) {
            case "auto":
                return EngineType.AUTO;
            case "ollama":
                return EngineType.OLLAMA;
            case "openai":
                return EngineType.OPENAI;
            default:
                throw new IllegalArgumentException("Unsupported engine.type value: " + engineType);
        }
    }

    public EngineType getEffectiveEngineType() {
        if (resolvedType == null) {
            synchronized (this) {
                if (resolvedType == null) {
                    resolvedType = detectEngineType();
                }
            }
        }
        return resolvedType;
    }

    @SuppressWarnings("deprecation")
    private EngineType detectEngineType() {
        if (engineHost.contains("ollama") || engineHost.contains(":11434") || engineHost.contains("ollama:") ) {
            logger.info("engine.host looks like Ollama: {}", engineHost);
            return EngineType.OLLAMA;
        }

        try {
            ClientResponse response = webClient.get()
                    .uri("/api/tags")
                    .exchange()
                    .block(Duration.ofSeconds(2));
            if (response != null) {
                try {
                    if (response.statusCode().is2xxSuccessful()
                            || response.statusCode().value() == 401
                            || response.statusCode().value() == 403) {
                        response.bodyToMono(Void.class).block(Duration.ofSeconds(1));
                        logger.info("Detected Ollama via /api/tags response {}", response.statusCode());
                        return EngineType.OLLAMA;
                    }
                    response.bodyToMono(Void.class).block(Duration.ofSeconds(1));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
            logger.debug("/api/tags probe failed, treating as OpenAI-compatible", ignored);
        }
        return EngineType.OPENAI;
    }

    public String translateRequestPath(String path) {
        EngineType type = getEffectiveEngineType();
        if (type == EngineType.OLLAMA) {
            if (path.equals("/v1/models") || path.startsWith("/v1/models/")) {
                return "/api/tags";
            }
            if (path.equals("/v1/chat/completions") || path.startsWith("/v1/chat/completions/")) {
                return "/api/chat";
            }
        }
        return path;
    }

    /**
     * Return true when the incoming `/v1` path should be forwarded to the engine host.
     * This prevents backend-internal endpoints (telemetry, connectors, approvals, etc.)
     * from being proxied to Ollama which doesn't implement them and returns 403.
     */
    public boolean shouldForward(String path) {
        EngineType type = getEffectiveEngineType();
        if (type == EngineType.OLLAMA) {
            // Whitelist engine-facing paths only
            if (path.equals("/v1/models") || path.startsWith("/v1/models/")) return true;
            if (path.equals("/v1/chat/completions") || path.startsWith("/v1/chat/completions/")) return true;
            if (path.equals("/v1/generate") || path.startsWith("/v1/generate/")) return true;
            if (path.equals("/v1/embeddings") || path.startsWith("/v1/embeddings/")) return true;
            // allow explicit API passthrough
            if (path.startsWith("/v1/api/")) return true;
        } else {
            // For OpenAI-compatible backends, map OpenAI-style endpoints
            if (path.equals("/v1/models") || path.startsWith("/v1/models/")) return true;
            if (path.equals("/v1/chat/completions") || path.startsWith("/v1/chat/completions/")) return true;
            if (path.equals("/v1/embeddings") || path.startsWith("/v1/embeddings/")) return true;
        }
        return false;
    }
}
