package org.opentron.backend.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(WebSocketHandler chatWebSocketHandler,
                                          AgentEventsWebSocketHandler agentEventsWebSocketHandler) {
        Map<String, WebSocketHandler> map = Map.of(
            "/v1/chat/stream", chatWebSocketHandler,
            "/v1/agents/events", agentEventsWebSocketHandler
        );
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(10);
        return mapping;
    }

    @Bean
    public AgentEventsWebSocketHandler agentEventsWebSocketHandler() {
        return new AgentEventsWebSocketHandler();
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
