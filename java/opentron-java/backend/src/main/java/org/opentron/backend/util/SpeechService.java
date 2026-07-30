package org.opentron.backend.util;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class SpeechService {

    @Value("${speech.backend:none}")
    private String speechBackend;

    @Value("${speech.ollama-host:http://localhost:11434}")
    private String ollamaHost;

    @Value("${speech.openai-key:}")
    private String openaiKey;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public Map<String, Object> getHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (speechBackend == null || speechBackend.equals("none")) {
            result.put("available", false);
            result.put("reason", "Speech backend not configured");
            return result;
        }

        if (speechBackend.equals("ollama")) {
            return checkOllamaHealth();
        } else if (speechBackend.equals("openai")) {
            return checkOpenAIHealth();
        }

        result.put("available", false);
        result.put("reason", "Unknown speech backend: " + speechBackend);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkOllamaHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaHost + "/api/tags"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Check if any models are available
                Map<String, Object> tags = objectMapper.readValue(response.body(), Map.class);
                Object models = tags.get("models");
                
                result.put("available", models != null);
                result.put("backend", "ollama");
                if (models == null) {
                    result.put("reason", "No transcription models loaded in Ollama");
                } else {
                    result.put("reason", "Ready");
                }
            } else {
                result.put("available", false);
                result.put("backend", "ollama");
                result.put("reason", "Ollama health check returned " + response.statusCode());
            }
        } catch (Exception e) {
            result.put("available", false);
            result.put("backend", "ollama");
            result.put("reason", "Cannot reach Ollama at " + ollamaHost);
        }
        return result;
    }

    private Map<String, Object> checkOpenAIHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (openaiKey == null || openaiKey.isBlank()) {
            result.put("available", false);
            result.put("backend", "openai");
            result.put("reason", "OpenAI API key not configured");
            return result;
        }

        try {
            // Quick test by listing models
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/models"))
                    .GET()
                    .header("Authorization", "Bearer " + openaiKey)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            result.put("available", response.statusCode() == 200);
            result.put("backend", "openai");
            if (response.statusCode() == 200) {
                result.put("reason", "Ready");
            } else {
                result.put("reason", "OpenAI API returned " + response.statusCode());
            }
        } catch (Exception e) {
            result.put("available", false);
            result.put("backend", "openai");
            result.put("reason", "Network error: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> transcribeAudio(MultipartFile audioFile) throws Exception {
        if (speechBackend == null || speechBackend.equals("none")) {
            throw new IllegalStateException("Speech backend not configured");
        }

        if (speechBackend.equals("ollama")) {
            return transcribeWithOllama(audioFile);
        } else if (speechBackend.equals("openai")) {
            return transcribeWithOpenAI(audioFile);
        }

        throw new IllegalStateException("Unknown speech backend: " + speechBackend);
    }

    private Map<String, Object> transcribeWithOllama(MultipartFile audioFile) throws Exception {
        // Ollama doesn't have native audio transcription, so we'd need to use an external service
        // or implement a workaround. For now, return a stub that could be enhanced.
        // In production, consider using whisper.cpp or a separate Whisper service.
        throw new UnsupportedOperationException("Ollama transcription not yet implemented. Use OpenAI backend.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> transcribeWithOpenAI(MultipartFile audioFile) throws Exception {
        if (openaiKey == null || openaiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        long startTime = System.currentTimeMillis();

        // Create multipart form data for OpenAI Whisper API
        String boundary = "----FormBoundary" + System.currentTimeMillis();
        String crlf = "\r\n";
        
        byte[] audioBytes = audioFile.getBytes();
        String filename = audioFile.getOriginalFilename() != null ? audioFile.getOriginalFilename() : "recording.webm";

        // Build multipart body
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(crlf);
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"").append(crlf);
        sb.append("Content-Type: ").append(audioFile.getContentType() != null ? audioFile.getContentType() : "audio/webm").append(crlf);
        sb.append(crlf);

        byte[] headerBytes = sb.toString().getBytes();
        
        String footer = crlf + "--" + boundary + "--" + crlf;
        byte[] footerBytes = footer.getBytes();

        // Combine all parts
        byte[] body = new byte[headerBytes.length + audioBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(audioBytes, 0, body, headerBytes.length, audioBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + audioBytes.length, footerBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Authorization", "Bearer " + openaiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(java.time.Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = "OpenAI Whisper API returned " + response.statusCode();
            try {
                Map<String, Object> error = objectMapper.readValue(response.body(), Map.class);
                Object errorDetail = error.get("error");
                if (errorDetail instanceof Map) {
                    errorMsg = ((Map<String, Object>) errorDetail).get("message").toString();
                }
            } catch (Exception e) {
                // Use default error message
            }
            throw new RuntimeException(errorMsg);
        }

        Map<String, Object> apiResponse = objectMapper.readValue(response.body(), Map.class);
        
        long duration = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", apiResponse.getOrDefault("text", ""));
        result.put("language", apiResponse.getOrDefault("language", null));
        result.put("confidence", null); // OpenAI Whisper doesn't return confidence
        result.put("duration_seconds", duration / 1000.0);

        return result;
    }
}
