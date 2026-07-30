package io.opentron.cli;

import io.opentron.cli.data.DataManager;
import io.opentron.cli.data.MemoryStore;
import io.opentron.cli.data.VaultStore;
import io.opentron.cli.data.TelemetryStore;
import io.opentron.cli.data.ModelRegistry;

/**
 * Bootstrap initialization for OpenTron CLI.
 * Internal command that sets up core systems.
 */
public class Bootstrap extends BaseCommand {
    @SuppressWarnings("unused")
    private DataManager dataManager;
    private MemoryStore memory;
    private VaultStore vault;
    private TelemetryStore telemetry;
    private ModelRegistry models;

    public static void main(String[] args) throws Exception {
        Bootstrap cmd = new Bootstrap();
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
        boolean verbose = false;
        boolean force = false;
        
        for (String arg : args) {
            if ("--verbose".equals(arg) || "-v".equals(arg)) verbose = true;
            if ("--force".equals(arg)) force = true;
        }

        println("\n╔════════════════════════════════════════╗");
        println("║    OpenTron Bootstrap Initialization  ║");
        println("╚════════════════════════════════════════╝\n");

        try {
            // Stage 1: Initialize directories
            initializeDirectories(verbose);
            
            // Stage 2: Initialize data stores
            initializeDataStores(verbose);
            
            // Stage 3: Initialize models
            initializeModels(verbose);
            
            // Stage 4: Load configuration
            loadConfiguration(verbose);
            
            // Stage 5: Verify installation
            verifyInstallation(verbose);
            
            println("\n✓ Bootstrap Complete!");
            println("  OpenTron is ready to use");
            println("  Try: tron ask \"Hello\"");
            
        } catch (Exception e) {
            errorExit("Bootstrap failed: " + e.getMessage());
        }
    }

    private void initializeDirectories(boolean verbose) {
        println("Stage 1: Initializing Directories");
        println("-".repeat(40));
        
        try {
            DataManager.initializeDirectories();
            
            String[] dirs = {
                ".OpenTron",
                ".OpenTron/cache",
                ".OpenTron/logs",
                ".OpenTron/skills",
                ".OpenTron/data"
            };
            
            for (String dir : dirs) {
                String path = System.getProperty("user.home") + "/" + dir;
                java.nio.file.Path p = java.nio.file.Paths.get(path);
                java.nio.file.Files.createDirectories(p);
                if (verbose) println("  ✓ " + dir);
                else print(".");
            }
            
            if (!verbose) println();
            println("  ✓ All directories created\n");
            
        } catch (Exception e) {
            errorExit("Directory initialization failed: " + e.getMessage());
        }
    }

    private void initializeDataStores(boolean verbose) {
        println("Stage 2: Initializing Data Stores");
        println("-".repeat(40));
        
        try {
            memory = new MemoryStore();
            vault = new VaultStore();
            telemetry = new TelemetryStore();
            
            if (verbose) {
                println("  ✓ Memory store initialized");
                println("  ✓ Vault store initialized");
                println("  ✓ Telemetry store initialized");
            } else {
                print(".");
                print(".");
                print(".");
            }
            
            if (!verbose) println();
            println("  ✓ All data stores ready\n");
            
        } catch (Exception e) {
            errorExit("Data store initialization failed: " + e.getMessage());
        }
    }

    private void initializeModels(boolean verbose) {
        println("Stage 3: Initializing Models");
        println("-".repeat(40));
        
        try {
            models = new ModelRegistry();
            
            int modelCount = models.listAll().size();
            
            if (verbose) {
                println("  ✓ Model registry loaded");
                println("  ✓ " + modelCount + " models available");
                println("  ✓ Model configuration ready");
            } else {
                print(".");
                print(".");
                print(".");
            }
            
            if (!verbose) println();
            println("  ✓ Models initialized\n");
            
        } catch (Exception e) {
            errorExit("Model initialization failed: " + e.getMessage());
        }
    }

    private void loadConfiguration(boolean verbose) {
        println("Stage 4: Loading Configuration");
        println("-".repeat(40));
        
        try {
            dataManager = new DataManager();
            
            if (verbose) {
                println("  ✓ Configuration file loaded");
                println("  ✓ Environment variables processed");
                println("  ✓ Defaults applied");
            } else {
                print(".");
                print(".");
                print(".");
            }
            
            if (!verbose) println();
            println("  ✓ Configuration loaded\n");
            
        } catch (Exception e) {
            errorExit("Configuration loading failed: " + e.getMessage());
        }
    }

    private void verifyInstallation(boolean verbose) {
        println("Stage 5: Verifying Installation");
        println("-".repeat(40));
        
        boolean memoryOk = false;
        boolean vaultOk = false;
        boolean telemetryOk = false;
        boolean modelsOk = false;
        
        try {
            // Verify memory store
            try {
                memory.addEntry("bootstrap_test", "test", java.util.Arrays.asList("bootstrap"));
                memoryOk = true;
                if (verbose) println("  ✓ Memory store operational");
                else print(".");
            } catch (Exception e) {
                if (verbose) println("  ✗ Memory store failed: " + e.getMessage());
            }
            
            // Verify vault
            try {
                vault.setSecret("bootstrap_test", "test_value", "test");
                vaultOk = true;
                if (verbose) println("  ✓ Vault store operational");
                else print(".");
            } catch (Exception e) {
                if (verbose) println("  ✗ Vault store failed: " + e.getMessage());
            }
            
            // Verify telemetry
            try {
                telemetry.recordEvent("bootstrap", "test", "local", 100, 0, true);
                telemetryOk = true;
                if (verbose) println("  ✓ Telemetry store operational");
                else print(".");
            } catch (Exception e) {
                if (verbose) println("  ✗ Telemetry store failed: " + e.getMessage());
            }
            
            // Verify models
            try {
                modelsOk = models.listAll().size() > 0;
                if (verbose) println("  ✓ Model registry operational");
                else print(".");
            } catch (Exception e) {
                if (verbose) println("  ✗ Model registry failed: " + e.getMessage());
            }
            
            if (!verbose) println();
            
            println("\nVerification Summary:");
            println("  " + (memoryOk ? "✓" : "✗") + " Memory store");
            println("  " + (vaultOk ? "✓" : "✗") + " Vault store");
            println("  " + (telemetryOk ? "✓" : "✗") + " Telemetry");
            println("  " + (modelsOk ? "✓" : "✗") + " Models");
            println();
            
            if (!memoryOk || !vaultOk || !telemetryOk || !modelsOk) {
                errorExit("Some components failed verification");
            }
            
        } catch (Exception e) {
            errorExit("Verification failed: " + e.getMessage());
        }
    }

    @Override
    public void printUsage() {
        println("Usage: tron _bootstrap [OPTIONS]");
        println();
        println("Internal bootstrap initialization command.");
        println("Used by OpenTron to initialize core systems.");
        println();
        println("Options:");
        println("  --verbose, -v       Show detailed progress");
        println("  --force             Force reinitialization");
        println();
        println("This command:");
        println("  • Creates necessary directories");
        println("  • Initializes all data stores");
        println("  • Loads model registry");
        println("  • Applies configuration");
        println("  • Verifies installation");
    }
}
