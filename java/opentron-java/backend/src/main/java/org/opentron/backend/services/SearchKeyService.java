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
 * Simple server-side storage for the search API key.
 * Persists the key to `configs/opentron/search-key.txt` in the workspace.
 * Note: for production consider encrypting or using a secrets store.
 */
@Service
public class SearchKeyService {

    private final Path envPath = Path.of(".env");

    private static final String KEY_NAME = "OPENTRON_SEARCH_KEY";
    private static final String PROVIDER_NAME = "OPENTRON_SEARCH_PROVIDER";

    public synchronized void saveKey(String key) throws IOException {
        saveKey(key, null);
    }

    public synchronized void saveKey(String key, String provider) throws IOException {
        // Load existing .env lines
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

        if (key == null || key.isBlank()) {
            map.remove(KEY_NAME);
        } else {
            map.put(KEY_NAME, key);
        }

        if (provider == null) {
            // leave existing provider unchanged
        } else if (provider.isBlank()) {
            map.remove(PROVIDER_NAME);
        } else {
            map.put(PROVIDER_NAME, provider);
        }

        // Reconstruct lines preserving unrelated entries
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        if (!Files.exists(envPath.getParent())) {
            // usually current directory; ensure parent exists only if path has parent
            try { Files.createDirectories(envPath.getParent()); } catch (Exception ignore) {}
        }
        Files.write(envPath, out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public synchronized String loadKey() {
        try {
            if (!Files.exists(envPath)) return null;
            List<String> lines = Files.readAllLines(envPath);
            for (String line : lines) {
                if (line == null || line.isBlank() || line.trim().startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (KEY_NAME.equals(k)) return v;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    public synchronized String loadProvider() {
        try {
            if (!Files.exists(envPath)) return null;
            List<String> lines = Files.readAllLines(envPath);
            for (String line : lines) {
                if (line == null || line.isBlank() || line.trim().startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (PROVIDER_NAME.equals(k)) return v;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }
}
