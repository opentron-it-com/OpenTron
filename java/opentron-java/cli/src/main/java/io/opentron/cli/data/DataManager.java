package io.opentron.cli.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Data persistence layer for OpenTron CLI.
 * Handles file I/O, JSON serialization, configuration management, and data storage.
 */
public class DataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.OpenTron";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.toml";
    @SuppressWarnings("unused")
    private static final String MEMORY_DB = CONFIG_DIR + "/memory.db";
    @SuppressWarnings("unused")
    private static final String VAULT_FILE = CONFIG_DIR + "/vault.json";
    @SuppressWarnings("unused")
    private static final String TELEMETRY_DB = CONFIG_DIR + "/telemetry.db";
    @SuppressWarnings("unused")
    private static final String DAEMON_LOG = CONFIG_DIR + "/daemon.log";

    /**
     * Initialize OpenTron data directories.
     */
    public static void initializeDirectories() throws IOException {
        Path configPath = Paths.get(CONFIG_DIR);
        Files.createDirectories(configPath);
        
        // Create default files if they don't exist
        createFileIfNotExists(Paths.get(CONFIG_DIR, "SOUL.md"),
            "# Agent Persona\n\nYou are Tron, a helpful personal AI assistant.\n");
        createFileIfNotExists(Paths.get(CONFIG_DIR, "MEMORY.md"),
            "# Agent Memory\n\nStore important information here.\n");
        createFileIfNotExists(Paths.get(CONFIG_DIR, "USER.md"),
            "# User Profile\n\nPersonal user information.\n");
        
        Files.createDirectories(Paths.get(CONFIG_DIR, "skills"));
    }

    /**
     * Create a file with default content if it doesn't exist.
     */
    private static void createFileIfNotExists(Path path, String defaultContent) throws IOException {
        if (!Files.exists(path)) {
            Files.write(path, defaultContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Load configuration from config.toml file.
     */
    public static Map<String, Object> loadConfig() throws IOException {
        Map<String, Object> config = new HashMap<>();
        
        if (Files.exists(Paths.get(CONFIG_FILE))) {
            String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE)), StandardCharsets.UTF_8);
            config = parseTomlConfig(content);
        } else {
            config = getDefaultConfig();
        }
        
        return config;
    }

    /**
     * Save configuration to config.toml file.
     */
    public static void saveConfig(Map<String, Object> config) throws IOException {
        Path configPath = Paths.get(CONFIG_FILE);
        String toml = mapToToml(config);
        Files.write(configPath, toml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get default configuration.
     */
    public static Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("engine", "ollama");
        config.put("model", "qwen2.5:7b");
        config.put("temperature", 0.7);
        config.put("max_tokens", 4096);
        config.put("host", "localhost");
        config.put("port", "11434");
        config.put("api_port", "8000");
        return config;
    }

    /**
     * Parse TOML configuration (simplified implementation).
     */
    private static Map<String, Object> parseTomlConfig(String content) {
        Map<String, Object> config = new HashMap<>();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    // Parse value type
                    if (value.equalsIgnoreCase("true")) {
                        config.put(key, true);
                    } else if (value.equalsIgnoreCase("false")) {
                        config.put(key, false);
                    } else if (value.matches("-?\\d+")) {
                        config.put(key, Long.parseLong(value));
                    } else if (value.matches("-?\\d+\\.\\d+")) {
                        config.put(key, Double.parseDouble(value));
                    } else {
                        // Remove quotes if present
                        config.put(key, value.replaceAll("^\"|\"$", ""));
                    }
                }
            }
        }
        
        return config;
    }

    /**
     * Convert map to TOML format.
     */
    private static String mapToToml(Map<String, Object> config) {
        StringBuilder sb = new StringBuilder();
        sb.append("# OpenTron Configuration\n");
        sb.append("# Generated configuration file\n\n");
        
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            
            if (value instanceof String) {
                sb.append(entry.getKey()).append(" = \"").append(value).append("\"\n");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(entry.getKey()).append(" = ").append(value).append("\n");
            } else if (value instanceof List) {
                sb.append(entry.getKey()).append(" = [");
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    sb.append("\"").append(list.get(i)).append("\"");
                    if (i < list.size() - 1) sb.append(", ");
                }
                sb.append("]\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Save data to JSON file.
     */
    public static void saveJson(Path path, Object data) throws IOException {
        String json = GSON.toJson(data);
        Files.createDirectories(path.getParent());
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Load JSON data from file.
     */
    public static <T> T loadJson(Path path, Class<T> type) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return GSON.fromJson(json, type);
    }

    /**
     * Append log entry.
     */
    public static void appendLog(String logFile, String entry) throws IOException {
        Path path = Paths.get(logFile);
        Files.createDirectories(path.getParent());
        
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] %s\n", timestamp, entry);
        
        if (Files.exists(path)) {
            String existing = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Files.write(path, (existing + logEntry).getBytes(StandardCharsets.UTF_8));
        } else {
            Files.write(path, logEntry.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Read last N lines from log file.
     */
    public static List<String> readLogTail(String logFile, int lines) throws IOException {
        Path path = Paths.get(logFile);
        if (!Files.exists(path)) return new ArrayList<>();
        
        List<String> allLines = Files.readAllLines(path);
        int startIndex = Math.max(0, allLines.size() - lines);
        return new ArrayList<>(allLines.subList(startIndex, allLines.size()));
    }

    /**
     * List all files in a directory.
     */
    public static List<String> listFiles(String directory) throws IOException {
        List<String> files = new ArrayList<>();
        Path dir = Paths.get(directory);
        
        if (Files.exists(dir)) {
            Files.list(dir)
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .forEach(files::add);
        }
        
        return files;
    }

    /**
     * Get file size in bytes.
     */
    public static long getFileSize(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return Files.size(path);
        }
        return 0;
    }

    /**
     * Format file size for display.
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    /**
     * Get configuration directory.
     */
    public static String getConfigDir() {
        return CONFIG_DIR;
    }

    /**
     * Delete file or directory.
     */
    public static void delete(String path) throws IOException {
        Path p = Paths.get(path);
        if (Files.exists(p)) {
            if (Files.isDirectory(p)) {
                Files.walk(p)
                    .sorted(Comparator.reverseOrder())
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            // Continue on error
                        }
                    });
            } else {
                Files.delete(p);
            }
        }
    }

    /**
     * Export data to CSV format.
     */
    public static void exportCsv(String outputFile, List<Map<String, Object>> data) throws IOException {
        if (data.isEmpty()) return;
        
        StringBuilder csv = new StringBuilder();
        
        // Header
        Set<String> headers = data.get(0).keySet();
        csv.append(String.join(",", headers)).append("\n");
        
        // Data rows
        for (Map<String, Object> row : data) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object value = row.get(header);
                String stringValue = value != null ? value.toString() : "";
                // Escape quotes and wrap in quotes if contains comma
                if (stringValue.contains(",") || stringValue.contains("\"")) {
                    stringValue = "\"" + stringValue.replace("\"", "\"\"") + "\"";
                }
                values.add(stringValue);
            }
            csv.append(String.join(",", values)).append("\n");
        }
        
        Files.write(Paths.get(outputFile), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Import data from CSV format.
     */
    public static List<Map<String, Object>> importCsv(String inputFile) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(inputFile));
        
        if (lines.isEmpty()) return data;
        
        String[] headers = parseCSVLine(lines.get(0));
        
        for (int i = 1; i < lines.size(); i++) {
            String[] values = parseCSVLine(lines.get(i));
            Map<String, Object> row = new HashMap<>();
            
            for (int j = 0; j < headers.length && j < values.length; j++) {
                row.put(headers[j], values[j]);
            }
            
            data.add(row);
        }
        
        return data;
    }

    /**
     * Parse a CSV line handling quotes.
     */
    private static String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        values.add(current.toString());
        return values.toArray(new String[0]);
    }

    /**
     * Watch for file changes (returns modification time).
     */
    public static long getLastModified(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return Files.getLastModifiedTime(path).toMillis();
        }
        return 0;
    }

    /**
     * Create a backup of a file.
     */
    public static void backup(String sourceFile) throws IOException {
        Path source = Paths.get(sourceFile);
        if (Files.exists(source)) {
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path backup = Paths.get(sourceFile + ".backup_" + timestamp);
            Files.copy(source, backup);
        }
    }
}
