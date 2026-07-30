package org.opentron.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class AgentEventsWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventsWebSocketHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        String query = uri.getQuery();
        String agentIdTemp = "unknown";
        if (query != null) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2 && "agent_id".equals(parts[0])) {
                    agentIdTemp = parts[1];
                    break;
                }
            }
        }

        final String agentId = agentIdTemp;
        logger.info("Agent events websocket connected for agentId={}", agentId);

        Flux<WebSocketMessage> outgoing = Flux.interval(Duration.ofSeconds(5))
                .map(index -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("type", "agent_event");
                    event.put("timestamp", System.currentTimeMillis());
                    event.put("agent_id", agentId);
                    event.put("event", "heartbeat");
                    event.put("sequence", index);
                    try {
                        return session.textMessage(mapper.writeValueAsString(event));
                    } catch (Exception e) {
                        logger.warn("Failed to serialize agent event", e);
                        return session.textMessage("{\"type\":\"error\",\"message\":\"serialization_failed\"}");
                    }
                })
                .doOnCancel(() -> logger.info("Agent events websocket cancelled for agentId={}", agentId));

        return session.send(outgoing)
                .doFinally(signal -> logger.info("Agent events websocket closed for agentId={} signal={}", agentId, signal));
    }
}
