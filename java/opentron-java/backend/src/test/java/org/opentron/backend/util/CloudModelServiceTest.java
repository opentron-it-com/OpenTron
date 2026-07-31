package org.opentron.backend.util;

import org.junit.jupiter.api.Test;
import org.opentron.backend.services.NetworkPolicyService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudModelServiceTest {

    @Test
    void buildAnthropicPayloadMovesSystemPromptToTopLevel() {
        // Provide a NetworkPolicyService to match CloudModelService constructor
        NetworkPolicyService policy = new NetworkPolicyService(true);
        CloudModelService service = new CloudModelService(policy);

        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", "You are a helpful assistant"),
            Map.of("role", "user", "content", "Hello there")
        );

        Map<String, Object> payload = service.buildAnthropicPayload("claude-haiku-4-5", messages, 0.7, 2000);

        assertEquals("You are a helpful assistant", payload.get("system"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> anthropicMessages = (List<Map<String, Object>>) payload.get("messages");
        assertEquals(1, anthropicMessages.size());
        assertEquals("user", anthropicMessages.get(0).get("role"));
        assertEquals("Hello there", anthropicMessages.get(0).get("content"));
    }
}
