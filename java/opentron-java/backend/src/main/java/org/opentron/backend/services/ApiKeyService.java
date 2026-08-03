package org.opentron.backend.services;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing cloud provider API keys (OpenAI, Anthropic, Google).
 * Persists keys to `.env` file in the workspace.
 */
@Service
public class ApiKeyService {

    private final Path envPath = Path.of(".env");

    private static final String GOOGLE_API_KEY = "GOOGLE_API_KEY";
    private static final String OPENAI_API_KEY = "OPENAI_API_KEY";
    private static final String ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";

    public synchronized void saveGoogleKey(String key) throws IOException {
        saveKey(GOOGLE_API_KEY, key);
    }

    public synchronized void saveOpenAIKey(String key) throws IOException {
        saveKey(OPENAI_API_KEY, key);
    }

    public synchronized void saveAnthropicKey(String key) throws IOException {
        saveKey(ANTHROPIC_API_KEY, key);
    }

    public synchronized String loadGoogleKey() {
        return loadKey(GOOGLE_API_KEY);
    }

    public synchronized String loadOpenAIKey() {
        return loadKey(OPENAI_API_KEY);
    }

    public synchronized String loadAnthropicKey() {
        return loadKey(ANTHROPIC_API_KEY);
    }

    private synchronized void saveKey(String keyName, String keyValue) throws IOException {
        List<String> lines = Files.exists(envPath) ? Files.readAllLines(envPath) : new ArrayList<>();
        Map<String, String> map = new LinkedHashMap<>();
        
        for (String line : lines) {
            if (line == null || line.isBlank() || line.trim().startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            map.put(k, v);
        }

        if (keyValue == null || keyValue.isBlank()) {
            map.remove(keyName);
        } else {
            map.put(keyName, keyValue);
        }

        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        
        if (!Files.exists(envPath.getParent())) {
            try { Files.createDirectories(envPath.getParent()); } catch (Exception ignore) {}
        }
        Files.write(envPath, out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private synchronized String loadKey(String keyName) {
        try {
            if (!Files.exists(envPath)) return null;
            List<String> lines = Files.readAllLines(envPath);
            for (String line : lines) {
                if (line == null || line.isBlank() || line.trim().startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (keyName.equals(k)) return v;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }
}
