package org.opentron.backend.util;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class ElevenLabsSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(ElevenLabsSpeechService.class);

    @Value("${elevenlabs.api-key:sk_35acb9084a826cd92cf7d3e646e181998f2680f3d2333f56}")
    private String elevenLabsApiKey;

    @Value("${elevenlabs.voice-id:onwK4e9ZLuTAKqWW03F9}")
    private String voiceId;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String ELEVENLABS_API_URL = "https://api.elevenlabs.io/v1";

    public Map<String, Object> getHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (elevenLabsApiKey == null || elevenLabsApiKey.isBlank()) {
            result.put("available", false);
            result.put("reason", "ElevenLabs API key not configured");
            return result;
        }

        try {
            // Quick test - check if API key is valid
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ELEVENLABS_API_URL + "/user"))
                    .GET()
                    .header("xi-api-key", elevenLabsApiKey)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            result.put("available", response.statusCode() == 200);
            result.put("backend", "elevenlabs");
            result.put("voice_id", voiceId);
            if (response.statusCode() == 200) {
                result.put("reason", "Ready");
            } else {
                result.put("reason", "ElevenLabs API returned " + response.statusCode());
            }
        } catch (Exception e) {
            result.put("available", false);
            result.put("backend", "elevenlabs");
            result.put("reason", "ElevenLabs API connection error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Transcribe audio using DeepSeek API (audio-to-text)
     * For now, returns mock transcription - can integrate with Whisper later
     */
    public Map<String, Object> transcribeAudio(MultipartFile audioFile) throws Exception {
        if (elevenLabsApiKey == null || elevenLabsApiKey.isBlank()) {
            throw new IllegalStateException("ElevenLabs API key not configured");
        }

        // Mock transcription for now
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", "Hello world");
        result.put("language", "en");
        result.put("confidence", 0.95);
        result.put("duration_seconds", audioFile.getSize() / 16000.0);
        
        logger.info("Transcription (mock): Hello world");
        return result;
    }

    /**
     * Text-to-Speech using ElevenLabs API
     * Returns audio URL and metadata
     */
    public Map<String, Object> synthesizeText(String text, String voice) throws Exception {
        if (elevenLabsApiKey == null || elevenLabsApiKey.isBlank()) {
            throw new IllegalStateException("ElevenLabs API key not configured");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }

        // Use provided voice or fall back to default
        String targetVoiceId = voice != null && !voice.isBlank() ? voice : voiceId;
        
        long startTime = System.currentTimeMillis();

        try {
            // Call ElevenLabs Text-to-Speech API
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("text", text);
            requestBody.put("model_id", "eleven_monolingual_v1");
            
            // Voice settings for natural speech
            Map<String, Object> voiceSettings = new LinkedHashMap<>();
            voiceSettings.put("stability", 0.5);
            voiceSettings.put("similarity_boost", 0.75);
            requestBody.put("voice_settings", voiceSettings);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ELEVENLABS_API_URL + "/text-to-speech/" + targetVoiceId))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .header("xi-api-key", elevenLabsApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() != 200) {
                throw new Exception("ElevenLabs API returned " + response.statusCode() + ": " + new String(response.body()));
            }

            // Convert audio bytes to base64 for transmission
            String audioBase64 = Base64.getEncoder().encodeToString(response.body());
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success");
            result.put("audio_base64", audioBase64);
            result.put("voice_id", targetVoiceId);
            result.put("text_length", text.length());
            result.put("audio_format", "mp3");
            result.put("duration_estimate_ms", (text.length() / 4.7 * 1000)); // Rough estimate
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("TTS generated: {} chars in {}ms", text.length(), duration);
            return result;
        } catch (Exception e) {
            logger.error("TTS generation error", e);
            throw new Exception("TTS generation failed: " + e.getMessage());
        }
    }

    /**
     * List available voices from ElevenLabs
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listVoices() throws Exception {
        if (elevenLabsApiKey == null || elevenLabsApiKey.isBlank()) {
            throw new IllegalStateException("ElevenLabs API key not configured");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ELEVENLABS_API_URL + "/voices"))
                    .GET()
                    .header("xi-api-key", elevenLabsApiKey)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Map.class);
            } else {
                throw new Exception("Failed to list voices: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new Exception("Failed to list ElevenLabs voices: " + e.getMessage());
        }
    }

    /**
     * Test ElevenLabs API connectivity
     */
    public boolean testConnection() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ELEVENLABS_API_URL + "/user"))
                    .GET()
                    .header("xi-api-key", elevenLabsApiKey)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
