package io.opentron.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.opentron.core.Utils;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Implement ``Tron doctor`` - verify setup and diagnose issues.
 */
public class DoctorCmd extends BaseCommand {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        DoctorCmd cmd = new DoctorCmd();
        try {
            cmd.execute(args);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void execute(String[] args) throws Exception {
        loadConfig();
        
        boolean verbose = false;
        for (String arg : args) {
            if ("--verbose".equals(arg)) {
                verbose = true;
            } else if ("--quiet".equals(arg)) {
                this.quiet = true;
            }
        }

        printBanner("OpenTron Diagnostics");
        
        int warnings = 0;
        int errors = 0;

        // Check configuration
        println("\n[*] Configuration...");
        String configPath = System.getProperty("user.home") + "/.OpenTron/config.toml";
        if (Files.exists(Paths.get(configPath))) {
            println("  ✓ Config file found at " + configPath);
        } else {
            println("  ✗ Config file not found. Run: tron init");
            errors++;
        }

        // Check inference engine
        println("\n[*] Inference Engine...");
        try {
            // Try to connect to Ollama
            String response = Utils.httpGet("http://localhost:11434/api/tags");
            if (response != null && !response.isEmpty()) {
                println("  ✓ Ollama running on localhost:11434");
                
                // Parse models
                JsonObject json = GSON.fromJson(response, JsonObject.class);
                JsonArray models = json.getAsJsonArray("models");
                if (models != null && models.size() > 0) {
                    println("  ✓ " + models.size() + " models available");
                    for (int i = 0; i < Math.min(3, models.size()); i++) {
                        println("    - " + models.get(i).getAsJsonObject().get("name").getAsString());
                    }
                } else {
                    println("  ! No models loaded. Run: ollama pull qwen2.5:7b");
                    warnings++;
                }
            } else {
                println("  ✗ Cannot connect to Ollama on localhost:11434");
                println("    Start Ollama with: ollama serve");
                errors++;
            }
        } catch (Exception e) {
            println("  ✗ Cannot connect to Ollama: " + e.getMessage());
            println("    Make sure Ollama is installed and running");
            errors++;
        }

        // Check Python environment (if applicable)
        println("\n[*] Dependencies...");
        try {
            String javaVersion = System.getProperty("java.version");
            println("  ✓ Java version: " + javaVersion);
        } catch (Exception e) {
            println("  ! Could not detect Java version");
        }

        // Check memory files
        println("\n[*] Memory Files...");
        String[] files = {"SOUL.md", "MEMORY.md", "USER.md"};
        for (String file : files) {
            String path = System.getProperty("user.home") + "/.OpenTron/" + file;
            if (Files.exists(Paths.get(path))) {
                println("  ✓ " + file + " found");
            } else {
                println("  ! " + file + " not found (will be created automatically)");
                warnings++;
            }
        }

        // Summary
        println("\n" + "=".repeat(50));
        if (errors == 0 && warnings == 0) {
            println("✓ All checks passed! Ready to use.");
            println("  Try: tron ask \"Hello\"");
            System.exit(0);
        } else if (errors > 0) {
            println("✗ " + errors + " critical issue(s) found. Please fix before using Tron.");
            System.exit(1);
        } else {
            println("⚠ " + warnings + " warning(s). Tron may not work optimally.");
            System.exit(0);
        }
    }

    @Override
    public void printUsage() {
        println("Usage: tron doctor [OPTIONS]");
        println();
        println("Verify OpenTron setup and diagnose issues");
        println();
        println("Options:");
        println("  --verbose   Show detailed diagnostic output");
    }
}
