package org.opentron.backend.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent telemetry store using JSON file.
 * Reloads from disk on each read to always get fresh data.
 */
@Component
public class PersistentTelemetryStore {

    private static final Logger logger = LoggerFactory.getLogger(PersistentTelemetryStore.class);

    private static final String DATA_DIR = System.getProperty("user.home") + "/.opentron";
    private static final String TELEMETRY_FILE = DATA_DIR + "/telemetry.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Long> data;

    public PersistentTelemetryStore() {
        this.data = loadFromDisk();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> loadFromDisk() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            File file = new File(TELEMETRY_FILE);
            if (file.exists()) {
                String content = new String(Files.readAllBytes(file.toPath()));
                Map<String, Long> loaded = mapper.readValue(content, Map.class);
                return loaded;
            }
        } catch (IOException e) {
            logger.warn("Error loading telemetry", e);
        }

        return new HashMap<>(Map.of(
            "total_requests", 0L,
            "total_tokens", 0L,
            "total_energy_j", 0L
        ));
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.write(Paths.get(TELEMETRY_FILE), json.getBytes());
        } catch (IOException e) {
            logger.warn("Error saving telemetry", e);
        }
    }

    public void addTokens(long tokens) {
        synchronized (data) {
            Object current = data.get("total_tokens");
            long total = 0L;
            if (current instanceof Long) total = (Long) current;
            else if (current instanceof Integer) total = ((Integer) current).longValue();
            else if (current instanceof Number) total = ((Number) current).longValue();
            data.put("total_tokens", total + tokens);
            saveToDisk();
        }
    }

    public void recordRequest() {
        synchronized (data) {
            Object current = data.get("total_requests");
            long total = 0L;
            if (current instanceof Long) total = (Long) current;
            else if (current instanceof Integer) total = ((Integer) current).longValue();
            else if (current instanceof Number) total = ((Number) current).longValue();
            data.put("total_requests", total + 1);
            saveToDisk();
        }
    }

    public void addEnergyJ(long energyJ) {
        synchronized (data) {
            Object current = data.get("total_energy_j");
            long total = 0L;
            if (current instanceof Long) total = (Long) current;
            else if (current instanceof Integer) total = ((Integer) current).longValue();
            else if (current instanceof Number) total = ((Number) current).longValue();
            data.put("total_energy_j", total + energyJ);
            saveToDisk();
        }
    }

    /**
     * Get total tokens - reload from disk for fresh data
     */
    public long getTotalTokens() {
        try {
            Map<String, Long> fresh = loadFromDisk();
            Object val = fresh.get("total_tokens");
            if (val instanceof Long) return (Long) val;
            if (val instanceof Integer) return ((Integer) val).longValue();
            if (val instanceof Number) return ((Number) val).longValue();
            return 0L;
        } catch (Exception e) {
            logger.warn("Error getting total tokens", e);
            return 0L;
        }
    }

    /**
     * Get total requests - reload from disk for fresh data
     */
    public long getTotalRequests() {
        try {
            Map<String, Long> fresh = loadFromDisk();
            Object val = fresh.get("total_requests");
            if (val instanceof Long) return (Long) val;
            if (val instanceof Integer) return ((Integer) val).longValue();
            if (val instanceof Number) return ((Number) val).longValue();
            return 0L;
        } catch (Exception e) {
            logger.warn("Error getting total requests", e);
            return 0L;
        }
    }

    /**
     * Get total energy - reload from disk for fresh data
     */
    public long getTotalEnergyJ() {
        try {
            Map<String, Long> fresh = loadFromDisk();
            Object val = fresh.get("total_energy_j");
            if (val instanceof Long) return (Long) val;
            if (val instanceof Integer) return ((Integer) val).longValue();
            if (val instanceof Number) return ((Number) val).longValue();
            return 0L;
        } catch (Exception e) {
            logger.warn("Error getting total energy", e);
            return 0L;
        }
    }

    public void reset() {
        synchronized (data) {
            data.clear();
            data.putAll(Map.of(
                "total_requests", 0L,
                "total_tokens", 0L,
                "total_energy_j", 0L
            ));
            saveToDisk();
        }
    }
}
