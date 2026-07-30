package io.opentron.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Implement ``Tron config`` - view and modify configuration.
 */
public class ConfigCmd extends BaseCommand {
    @SuppressWarnings("unused")
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        ConfigCmd cmd = new ConfigCmd();
        try {
            cmd.execute(args);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void execute(String[] args) throws Exception {
        loadConfig();

        if (args.length == 0) {
            // Show all config
            println("Current Configuration:");
            for (String key : config.keySet()) {
                println("  " + key + " = " + config.get(key));
            }
            return;
        }

        String subcommand = args[0];
        
        if ("get".equals(subcommand)) {
            if (args.length < 2) {
                errorExit("Usage: tron config get <key>");
            }
            String key = args[1];
            String value = config.get(key);
            if (value != null) {
                println(value);
            } else {
                println("Key not found: " + key);
            }
        } else if ("set".equals(subcommand)) {
            if (args.length < 3) {
                errorExit("Usage: tron config set <key> <value>");
            }
            String key = args[1];
            String value = args[2];
            config.put(key, value);
            println("✓ Set " + key + " = " + value);
            
        } else if ("list".equals(subcommand)) {
            println("Available configuration keys:");
            for (String key : config.keySet()) {
                println("  " + key);
            }
        } else {
            errorExit("Unknown subcommand: " + subcommand);
        }
    }

    @Override
    public void printUsage() {
        println("Usage: tron config <subcommand> [OPTIONS]");
        println();
        println("Subcommands:");
        println("  get <key>           Get a configuration value");
        println("  set <key> <value>   Set a configuration value");
        println("  list                List all available keys");
    }
}
