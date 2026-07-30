package org.opentron.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;

/**
 * ModelPreloader: Asynchronously pre-loads the fast Mistral model.
 * Non-blocking - if Ollama isn't ready, it skips silently.
 */
@Component
public class ModelPreloader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ModelPreloader.class);

    private static final String MODEL = "mistral";
    private static final String OLLAMA_API = "http://127.0.0.1:11434/api/generate";

    @Override
    public void run(String... args) {
        // Run warmup in background thread - don't block startup
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Wait 3 seconds for Ollama to stabilize
                warmupModel();
            } catch (Exception e) {
                logger.warn("Warmup skipped", e);
            }
        }).start();
    }

    @SuppressWarnings({ "deprecation", "unused" })
    private void warmupModel() {
        logger.info("Starting async model warmup...");
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(OLLAMA_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            String json = "{\"model\":\"" + MODEL + "\",\"prompt\":\"hello\",\"stream\":false}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                logger.info("Model warmup complete - Mistral ready for instant responses");
            } else {
                logger.warn("Warmup HTTP {}", code);
            }
            
            // Drain response
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // Discard
                }
            }
        } catch (Exception e) {
            logger.warn("Warmup failed (non-critical)", e);
        }
    }
}
