package org.opentron.backend.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.opentron.backend.util.EngineRouting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Forwards API requests to the LLM engine (Ollama, vLLM, etc.)
 * Only forwards endpoints NOT handled by dedicated controllers.
 */
@RestController
@RequestMapping("/v1")
public class ForwardingController {

    private static final Logger logger = LoggerFactory.getLogger(ForwardingController.class);

    private final WebClient webClient;
    private final EngineRouting engineRouting;

    public ForwardingController(WebClient webClient, EngineRouting engineRouting) {
        this.webClient = webClient;
        this.engineRouting = engineRouting;
    }

    @SuppressWarnings("deprecation")
    @RequestMapping(path = "/**")
    public Mono<ResponseEntity<Flux<DataBuffer>>> proxy(HttpServletRequest request) {
        try {
            String path = request.getRequestURI();
            String queryString = request.getQueryString();
            String translatedPath = engineRouting.translateRequestPath(path);
            // Only forward requests that are meant for the engine. If this path
            // is a backend-internal endpoint (telemetry, connectors, etc.) we
            // should not proxy it to Ollama — return 404 instead of forwarding.
            if (!engineRouting.shouldForward(path)) {
                logger.debug("Not forwarding path to engine: {}", path);
                return Mono.just(ResponseEntity.status(404).body(Flux.empty()));
            }
            String uri = translatedPath + (queryString != null ? "?" + queryString : "");
            String method = request.getMethod();

            logger.info("Forwarding to engine: {} {}", method, uri);

            WebClient.RequestBodySpec spec = webClient.method(HttpMethod.valueOf(method)).uri(uri);

            HttpHeaders headers = new HttpHeaders();
            if (request.getHeaderNames() != null) {
                for (String headerName : java.util.Collections.list(request.getHeaderNames())) {
                    if (shouldSkipHeader(headerName)) {
                        continue;
                    }
                    for (String headerValue : java.util.Collections.list(request.getHeaders(headerName))) {
                        headers.add(headerName, headerValue);
                    }
                }
            }
            spec.headers(httpHeaders -> httpHeaders.addAll(headers));

            boolean hasBody = request.getContentLengthLong() > 0 || 
                            "POST".equalsIgnoreCase(method) || 
                            "PUT".equalsIgnoreCase(method) || 
                            "PATCH".equalsIgnoreCase(method);

            if (hasBody) {
                Flux<DataBuffer> bodyFlux = DataBufferUtils.readInputStream(
                    () -> request.getInputStream(), 
                    new DefaultDataBufferFactory(), 
                    4096);
                return spec.body(BodyInserters.fromDataBuffers(bodyFlux))
                    .exchange()
                    .map(response -> buildResponse(response))
                    .onErrorResume(e -> handleForwardError(e, method, uri));
            }

            return spec.exchange()
                .map(response -> buildResponse(response))
                .onErrorResume(e -> handleForwardError(e, method, uri));

        } catch (Exception ex) {
            logger.error("Error in forwarding proxy", ex);
            return Mono.just(ResponseEntity.status(500).body(Flux.empty()));
        }
    }

    private boolean shouldSkipHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.equals("content-length") || 
               lower.equals("authorization") || 
               lower.equals("host") || 
               lower.equals("connection");
    }

    private ResponseEntity<Flux<DataBuffer>> buildResponse(ClientResponse response) {
        HttpHeaders responseHeaders = new HttpHeaders();
        response.headers().asHttpHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                responseHeaders.put(name, values);
            }
        });
        return ResponseEntity
            .status(response.statusCode())
            .headers(responseHeaders)
            .body(response.bodyToFlux(DataBuffer.class));
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> handleForwardError(Throwable e, String method, String uri) {
        logger.warn("Forward error for {} {}: {}", method, uri, e.getMessage(), e);
        return Mono.just(ResponseEntity.status(502).body(Flux.empty()));
    }
}
