package io.opentron.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all CLI commands.
 * Provides common functionality like config loading, engine resolution, and console output.
 */
public abstract class BaseCommand {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    protected Map<String, String> config;
    protected boolean verbose;
    protected boolean quiet;

    public BaseCommand() {
        this.config = new HashMap<>();
        this.verbose = false;
        this.quiet = false;
    }

    /**
     * Execute the command with the given arguments.
     */
    public abstract void execute(String[] args) throws Exception;

    /**
     * Print usage/help information for this command.
     */
    public abstract void printUsage();

    /**
     * Load configuration from the OpenTron config directory.
     */
    protected void loadConfig() {
        try {
            config.put("engine", "ollama");
            config.put("model", "qwen2.5:7b");
            config.put("host", "localhost");
            config.put("port", "11434");
        } catch (Exception e) {
            if (verbose) {
                System.err.println("Warning: Could not load config: " + e.getMessage());
            }
        }
    }

    /**
     * Print information to stdout (respecting --quiet flag).
     */
    protected void println(String msg) {
        if (!quiet) {
            System.out.println(msg);
        }
    }
    
    /**
     * Print a blank line.
     */
    protected void println() {
        if (!quiet) {
            System.out.println();
        }
    }
    
    /**
     * Print information to stdout without newline (respecting --quiet flag).
     */
    protected void print(String msg) {
        if (!quiet) {
            System.out.print(msg);
        }
    }

    /**
     * Print information to stderr (respecting --quiet flag).
     */
    protected void printlnErr(String msg) {
        if (!quiet) {
            System.err.println(msg);
        }
    }

    /**
     * Print debug information (only if --verbose flag is set).
     */
    protected void debug(String msg) {
        if (verbose) {
            System.err.println("[DEBUG] " + msg);
        }
    }

    /**
     * Print a formatted error and exit.
     */
    protected void errorExit(String msg) {
        System.err.println("✗ Error: " + msg);
        System.exit(1);
    }

    /**
     * Helper to parse --verbose and --quiet flags common to all commands.
     */
    protected int parseGlobalFlags(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--verbose".equals(args[i])) {
                this.verbose = true;
                return i;
            } else if ("--quiet".equals(args[i])) {
                this.quiet = true;
                return i;
            }
        }
        return -1;
    }

    /**
     * Banner printer helper.
     */
    protected void printBanner(String title) {
        println("\n╭" + "─".repeat(Math.max(0, title.length() + 2)) + "╮");
        println("│ " + title + " │");
        println("╰" + "─".repeat(Math.max(0, title.length() + 2)) + "╯\n");
    }
}
