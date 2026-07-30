package io.opentron.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Implement ``Tron init`` - detect hardware, generate config, write to disk.
 */
public class InitCmd extends BaseCommand {
    @SuppressWarnings("unused")
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        InitCmd cmd = new InitCmd();
        try {
            cmd.execute(args);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            if (cmd.verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void execute(String[] args) throws Exception {
        loadConfig();
        
        boolean force = false;
        boolean fullConfig = false;
        boolean noDownload = false;
        boolean skipScan = false;
        String engine = null;
        String host = null;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--force".equals(arg)) {
                force = true;
            } else if ("--full".equals(arg)) {
                fullConfig = true;
            } else if ("--no-download".equals(arg)) {
                noDownload = true;
            } else if ("--no-scan".equals(arg)) {
                skipScan = true;
            } else if ("--engine".equals(arg) && i + 1 < args.length) {
                engine = args[++i];
            } else if ("--host".equals(arg) && i + 1 < args.length) {
                host = args[++i];
            } else if ("--verbose".equals(arg)) {
                this.verbose = true;
            } else if ("--quiet".equals(arg)) {
                this.quiet = true;
            }
        }

        printBanner("OpenTron Configuration Setup");

        // Check if config already exists
        String configPath = System.getProperty("user.home") + "/.OpenTron/config.toml";
        if (Files.exists(Paths.get(configPath)) && !force) {
            println("✗ Config already exists at " + configPath);
            println("Use --force to overwrite.");
            System.exit(1);
        }

        println("Detecting hardware...");
        Map<String, String> hwInfo = detectHardware();
        println("  Platform : " + hwInfo.get("platform"));
        println("  CPU      : " + hwInfo.get("cpu_brand") + " (" + hwInfo.get("cpu_count") + " cores)");
        println("  RAM      : " + hwInfo.get("ram_gb") + " GB");

        // Select engine
        if (engine == null) {
            engine = "ollama"; // Default recommendation
            println("\nAvailable engines: ollama, vllm, sglang, llamacpp, mlx, lmstudio, exo, nexa");
        }

        println("\nGenerating configuration for engine: " + engine);
        String toml = generateConfig(hwInfo, engine, fullConfig, host);

        // Create config directory
        Files.createDirectories(Paths.get(System.getProperty("user.home") + "/.OpenTron"));
        Files.write(Paths.get(configPath), toml.getBytes());

        println("\n[✓] Config written to " + configPath);

        // Create persona files
        String soulmPath = System.getProperty("user.home") + "/.OpenTron/SOUL.md";
        if (!Files.exists(Paths.get(soulmPath))) {
            Files.write(Paths.get(soulmPath), "# Agent Persona\n\nYou are Tron, a helpful personal AI assistant.\n".getBytes());
        }

        println("\n[✓] Setup complete!");
        println("\nNext steps:");
        println("  1. Install and start Ollama:");
        println("     curl -fsSL https://ollama.com/install.sh | sh");
        println("     ollama serve");
        println("  2. Pull a model:");
        println("     ollama pull qwen2.5:7b");
        println("  3. Try it out:");
        println("     Tron ask \"Hello\"");
    }

    private Map<String, String> detectHardware() {
        Map<String, String> hw = new HashMap<>();
        Runtime rt = Runtime.getRuntime();
        
        hw.put("platform", System.getProperty("os.name"));
        hw.put("cpu_count", String.valueOf(rt.availableProcessors()));
        hw.put("cpu_brand", System.getProperty("os.arch"));
        
        long ramBytes = rt.totalMemory();
        long ramGb = ramBytes / (1024L * 1024L * 1024L);
        hw.put("ram_gb", String.valueOf(ramGb));
        
        return hw;
    }

    private String generateConfig(Map<String, String> hwInfo, String engine, boolean fullConfig, String host) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# OpenTron Configuration\n");
        sb.append("# Generated for: ").append(hwInfo.get("platform")).append("\n\n");
        
        sb.append("[intelligence]\n");
        sb.append("engine = \"").append(engine).append("\"\n");
        sb.append("model = \"qwen2.5:7b\"\n");
        sb.append("temperature = 0.7\n");
        sb.append("max_tokens = 4096\n\n");
        
        sb.append("[server]\n");
        sb.append("host = \"localhost\"\n");
        sb.append("port = 8000\n");
        if (host != null) {
            sb.append("remote_engine = \"").append(host).append("\"\n");
        }
        sb.append("\n");
        
        if (fullConfig) {
            sb.append("[memory]\n");
            sb.append("enabled = true\n");
            sb.append("backend = \"sqlite\"\n");
            sb.append("db_path = \"~/.OpenTron/memory.db\"\n\n");
            
            sb.append("[telemetry]\n");
            sb.append("enabled = true\n");
            sb.append("db_path = \"~/.OpenTron/telemetry.db\"\n\n");
        }
        
        return sb.toString();
    }

    @Override
    public void printUsage() {
        println("Usage: tron init [OPTIONS]");
        println();
        println("Detect hardware and generate ~/.OpenTron/config.toml");
        println();
        println("Options:");
        println("  --force              Overwrite existing config");
        println("  --full               Generate full reference config");
        println("  --engine ENGINE      Specify inference engine");
        println("  --host HOST          Remote engine URL");
        println("  --no-download        Skip model download prompt");
        println("  --no-scan            Skip security audit");
    }
}
