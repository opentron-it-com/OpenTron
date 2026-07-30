package org.opentron.backend.controllers;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;

import org.opentron.backend.util.EngineRouting;
import org.opentron.backend.util.ResilienceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.opentron.backend.storage.entities.PullJob;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/v1/models")
public class ModelsController {

    private static final Logger logger = LoggerFactory.getLogger(ModelsController.class);

    private final WebClient webClient;
    private final EngineRouting engineRouting;
    private final org.opentron.backend.services.PullService pullService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelsController(WebClient webClient, EngineRouting engineRouting, org.opentron.backend.services.PullService pullService) {
        this.webClient = webClient;
        this.engineRouting = engineRouting;
        this.pullService = pullService;
    }

    @SuppressWarnings("unchecked")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> listModels() {
        String targetPath = engineRouting.translateRequestPath("/v1/models");
        return ResilienceUtil.withResilienceNonStreaming(
            webClient.get()
                    .uri(targetPath)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        try {
                            Map<String, Object> ollamaResp = objectMapper.readValue(response, Map.class);
                            List<Map<String, Object>> ollamaModels = (List<Map<String, Object>>) ollamaResp.get("models");

                            List<Map<String, Object>> transformed = new ArrayList<>();
                            if (ollamaModels != null) {
                                for (Map<String, Object> ollamaModel : ollamaModels) {
                                    Map<String, Object> modelInfo = new LinkedHashMap<>();
                                    String modelName = (String) ollamaModel.get("name");
                                    if (modelName == null) {
                                        modelName = (String) ollamaModel.get("model");
                                    }
                                    modelInfo.put("id", modelName != null ? modelName : "unknown");
                                    modelInfo.put("object", "model");
                                    modelInfo.put("created", System.currentTimeMillis() / 1000);
                                    modelInfo.put("owned_by", "ollama");
                                    transformed.add(modelInfo);
                                }
                            }

                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("data", transformed);

                            String jsonResponse = objectMapper.writeValueAsString(result);
                            logger.info("Transformed {} models", transformed.size());

                            return ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(jsonResponse);
                        } catch (Exception e) {
                            logger.warn("Error transforming models response", e);
                            try {
                                Map<String, Object> fallback = new LinkedHashMap<>();
                                fallback.put("data", new ArrayList<>());
                                return ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(objectMapper.writeValueAsString(fallback));
                            } catch (Exception ex) {
                                return ResponseEntity.status(502)
                                        .body("{\"data\":[],\"error\":\"models-service-unavailable\"}");
                            }
                        }
                    })
                    .onErrorReturn(ResponseEntity.status(502)
                            .body("{\"data\":[],\"error\":\"models-service-unavailable\"}"))
        );
    }

    @PostMapping(value = "/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> pullModel(@RequestBody org.opentron.backend.dto.PullModelRequest request) {
        String modelName = request.getName() == null ? (request.getModel() == null ? "" : request.getModel()) : request.getName();

        if (modelName.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "model name required")));
        }

        PullJob job = pullService.createJob(modelName);
        // start pull asynchronously
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);
        pullService.runPull(job, payload);

        Map<String, Object> resp = Map.of(
            "job_id", job.getJobId(),
            "status", job.getStatus()
        );
        return Mono.just(ResponseEntity.accepted().body(resp));
    }

    @GetMapping(value = "/pull/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getPullStatus(@PathVariable String jobId) {
        PullJob job = pullService.getJob(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        Map<String, Object> resp = Map.of(
            "job_id", job.getJobId(),
            "status", job.getStatus(),
            "message", job.getMessage(),
            "created_at", job.getCreatedAt(),
            "started_at", job.getStartedAt(),
            "completed_at", job.getCompletedAt()
        );
        return ResponseEntity.ok(resp);
    }

    @GetMapping(value = "/pull/{jobId}/events", produces = "text/event-stream")
    public Flux<String> streamPullEvents(@PathVariable String jobId) {
        return pullService.streamEvents(jobId)
                .map(chunk -> "data: " + chunk + "\n\n")
                .switchIfEmpty(Flux.just("data: {\"error\":\"no_events\"}\n\n"));
    }
}
