package org.opentron.backend.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.opentron.backend.OpentronBackendApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = OpentronBackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConnectorsControllerIntegrationTest {

    private static HttpServer engineStub;

    @BeforeAll
    public void startStub() throws Exception {
        if (engineStub == null) {
            engineStub = HttpServer.create(new InetSocketAddress(0), 0);
            engineStub.createContext("/", new ForwardAllHandler());
            engineStub.setExecutor(r -> new Thread(r));
            engineStub.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (engineStub != null) engineStub.stop(0);
            }));
        }
    }

    @AfterAll
    public void stopStub() {
        if (engineStub != null) engineStub.stop(0);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        try {
            if (engineStub == null) {
                engineStub = HttpServer.create(new InetSocketAddress(0), 0);
                engineStub.createContext("/", new ForwardAllHandler());
                engineStub.setExecutor(r -> new Thread(r));
                engineStub.start();
            }
            int port = engineStub.getAddress().getPort();
            registry.add("engine.host", () -> "http://127.0.0.1:" + port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Value("${local.server.port}")
    int localPort;

    @Test
    public void connectorsEndpointShouldNotBeProxiedToEngine() {
        WebClient client = WebClient.builder().baseUrl("http://127.0.0.1:" + localPort).build();
        String response = client.get().uri("/v1/connectors")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(5));

        assertNotNull(response);
        assertTrue(response.contains("\"connectors\""));
        assertTrue(response.contains("[]"));
    }

    static class ForwardAllHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = "{\"forwarded\":true}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
