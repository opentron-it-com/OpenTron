package org.opentron.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * ModelPreloader: Pre-loads all fast Ollama models into VRAM on startup.
 * Ensures instant response times by having models ready before first request.
 * Non-blocking - runs async in background.
 */
@Component
public class ModelPreloader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ModelPreloader.class);

    private static final String OLLAMA_API = "http://127.0.0.1:11434/api/generate";
    
    // All 4 optimized models - preload them all
    private static final List<String> MODELS = Arrays.asList(
        "llama3.2:1b",          // QA - ultra-fast
        "llama3.2:3b",          // Knowledge - ultra-fast
        "qwen2.5-coder:7b",     // Backend, Frontend, DevOps - fast code
        "deepseek-coder:6.7b"   // Backup code specialist
    );

    @Override
    public void run(String... args) {
        // BLOCKING: Wait for ALL models to preload before app accepts requests
        logger.info("Preloading ALL models BEFORE startup...");
        try {
            Thread.sleep(2000); // Wait 2 seconds for Ollama to stabilize
            preloadAllModels(); // Blocks until complete
            logger.info("✓ All models ready! App ready to accept requests.");
        } catch (Exception e) {
            logger.error("Model preload failed", e);
            throw new RuntimeException("Critical: Cannot start without preloaded models", e);
        }
    }

    private void preloadAllModels() {
        logger.info("========================================");
        logger.info("Starting Model VRAM Preload");
        logger.info("Loading {} models into VRAM for instant responses...", MODELS.size());
        logger.info("========================================");
        
        long startTime = System.currentTimeMillis();
        int loaded = 0;
        
        for (String model : MODELS) {
            try {
                long modelStart = System.currentTimeMillis();
                boolean success = preloadModel(model);
                long modelDuration = System.currentTimeMillis() - modelStart;
                
                if (success) {
                    loaded++;
                    logger.info("✓ {} loaded in {}ms", model, modelDuration);
                } else {
                    logger.warn("✗ {} failed to load", model);
                }
            } catch (Exception e) {
                logger.warn("✗ {} error: {}", model, e.getMessage());
            }
        }
        
        long totalDuration = System.currentTimeMillis() - startTime;
        logger.info("========================================");
        logger.info("Preload Complete: {}/{} models loaded in {}s", 
            loaded, MODELS.size(), totalDuration / 1000.0);
        logger.info("All models ready for instant responses!");
        logger.info("========================================");
    }

    @SuppressWarnings({ "deprecation" })
    private boolean preloadModel(String model) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(OLLAMA_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(180000); // 3 minute timeout for loading large models
            conn.setDoOutput(true);

            String json = "{\"model\":\"" + model + "\",\"prompt\":\"hi\",\"stream\":false}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                // Drain response to completion
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // Discard - just need to consume the response
                    }
                }
                return true;
            } else {
                logger.warn("Model {} HTTP {}", model, code);
                return false;
            }
        } catch (java.io.InterruptedIOException e) {
            logger.warn("Model {} load interrupted (timeout)", model);
            return false;
        } catch (Exception e) {
            logger.warn("Model {} load error: {}", model, e.getMessage());
            return false;
        }
    }
}
