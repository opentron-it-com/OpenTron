package org.opentron.backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.opentron.backend.util.EngineRouting;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class ReactiveChatWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveChatWebSocketHandler.class);

    private final WebClient webClient;
    private final EngineRouting engineRouting;
    

    public ReactiveChatWebSocketHandler(WebClient webClient, EngineRouting engineRouting) {
        this.webClient = webClient;
        this.engineRouting = engineRouting;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        logger.info("new websocket connection");

        // Receive first message (chat request), POST to engine, stream SSE chunks back
        return session.receive().next()
                .flatMap(firstMsg -> {
                    String payload = firstMsg.getPayloadAsText();
                    logger.debug("received payload: {}", payload);

                    // POST to engine's chat API path with stream=true
                    String targetPath = engineRouting.translateRequestPath("/v1/chat/completions");
                    return webClient.post()
                            .uri(targetPath)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToFlux(DataBuffer.class)
                            .flatMap(buf -> {
                                byte[] bytes = new byte[buf.readableByteCount()];
                                buf.read(bytes);
                                DataBufferUtils.release(buf);
                                String chunk = new String(bytes, StandardCharsets.UTF_8);
                                
                                // Parse SSE data: lines
                                return Flux.fromArray(chunk.split("\n"))
                                        .filter(line -> line.startsWith("data: "))
                                        .map(line -> line.substring(6).trim())
                                        .flatMap(data -> {
                                            if ("[DONE]".equals(data) || data.isEmpty()) {
                                                return Mono.empty();
                                            }
                                            // Relay SSE data as websocket text message
                                            return session.send(Mono.just(session.textMessage(data)))
                                                    .then(Mono.just(data));
                                        });
                            })
                            .doOnError(e -> logger.warn("engine error", e))
                            .doOnCancel(() -> logger.debug("stream cancelled"))
                            .then();
                })
                .doFinally(sig -> {
                    try {
                        session.close().subscribe();
                    } catch (Exception ignored) {}
                })
                .onErrorResume(e -> {
                    logger.error("WebSocket handler error", e);
                    return session.send(Mono.just(session.textMessage("{\"error\":\"" + e.getMessage() + "\"}")))
                            .then();
                });
    }
}
