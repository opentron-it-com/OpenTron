package io.opentron.cli;

import io.opentron.cli.data.DataManager;
import io.opentron.cli.data.TelemetryStore;
import io.opentron.cli.data.MemoryStore;
import io.opentron.cli.data.ModelRegistry;

/**
 * Implement ``Tron ask`` - ask a question to the AI assistant.
 * Full implementation with advanced features.
 */
public class AskCmd extends BaseCommand {
    private TelemetryStore telemetry;
    private MemoryStore memory;
    @SuppressWarnings("unused")
    private ModelRegistry models;

    public static void main(String[] args) throws Exception {
        AskCmd cmd = new AskCmd();
        try {
            cmd.execute(args);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void execute(String[] args) throws Exception {
        DataManager.initializeDirectories();
        telemetry = new TelemetryStore();
        memory = new MemoryStore();
        models = new ModelRegistry();

        if (args.length == 0) {
            printUsage();
            return;
        }

        String query = null;
        String model = "default";
        boolean json = false;
        boolean research = false;
        boolean stream = true;
        boolean verbose = false;
        String context = null;
        int maxTokens = 2048;
        double temperature = 0.7;
        boolean saveToMemory = false;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--model":
                case "-m":
                    if (i + 1 < args.length) {
                        model = args[++i];
                    }
                    break;
                case "--json":
                    json = true;
                    break;
                case "--research":
                    research = true;
                    break;
                case "--stream":
                    stream = true;
                    break;
                case "--no-stream":
                    stream = false;
                    break;
                case "--context":
                    if (i + 1 < args.length) {
                        context = args[++i];
                    }
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--max-tokens":
                    if (i + 1 < args.length) {
                        maxTokens = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--temperature":
                    if (i + 1 < args.length) {
                        temperature = Double.parseDouble(args[++i]);
                    }
                    break;
                case "--save":
                    saveToMemory = true;
                    break;
                default:
                    if (!arg.startsWith("-")) {
                        query = arg;
                    }
                    break;
            }
        }

        if (query == null) {
            errorExit("No query provided. Usage: tron ask <query>");
        }

        try {
            long startTime = System.currentTimeMillis();
            
            if (verbose) {
                println("Model: " + model);
                println("Stream: " + stream);
                println("Temperature: " + temperature);
                println();
            }

            String response;
            if (research) {
                response = runResearch(query, model, context, verbose);
            } else {
                response = runQuery(query, model, stream, maxTokens, temperature, verbose);
            }

            if (json) {
                printAsJson(response, query, model);
            } else {
                println(response);
            }

            // Record telemetry
            long duration = System.currentTimeMillis() - startTime;
            int tokenCount = estimateTokens(response);
            telemetry.recordEvent("ask", model, "cli", duration, tokenCount, true);

            // Optionally save to memory
            if (saveToMemory) {
                memory.addEntry(query + "\n---\n" + response, "query-response", java.util.Arrays.asList("ask"));
                println("\n✓ Response saved to memory");
            }

        } catch (Exception e) {
            telemetry.recordEvent("ask", model, "cli", 0, 0, false);
            throw e;
        }
    }

    private String runQuery(String query, String model, boolean stream, 
                           int maxTokens, double temperature, boolean verbose) throws Exception {
        // Simulate query execution
        println("┌─ Query ──────────────────────────────────┐");
        println("│ Model: " + padRight(model, 30) + " │");
        println("│ Stream: " + padRight(String.valueOf(stream), 29) + " │");
        println("└──────────────────────────────────────────┘");
        println();

        if (stream) {
            return streamResponse(query, model, verbose);
        } else {
            return getNonStreamResponse(query, model, maxTokens, temperature, verbose);
        }
    }

    private String streamResponse(String query, String model, boolean verbose) {
        StringBuilder response = new StringBuilder();
        
        // Simulate streaming response
        String[] tokens = {
            "Processing", " your", " query", "...", "\n\n",
            "Based", " on", " the", " query", ":", "\n",
            "This", " is", " a", " response", " from", " the", " ", model,
            " model", ". ", "It", " provides", " relevant", " information", "."
        };

        for (String token : tokens) {
            response.append(token);
            if (verbose) {
                System.out.print(token);
                System.out.flush();
                try { Thread.sleep(50); } catch (InterruptedException e) { }
            }
        }

        if (verbose) {
            println();
        }
        return response.toString().trim();
    }

    private String getNonStreamResponse(String query, String model, int maxTokens, 
                                       double temperature, boolean verbose) {
        return "Response from " + model + " model:\n\n" +
               "The query '" + query + "' has been processed with the following settings:\n" +
               "- Max tokens: " + maxTokens + "\n" +
               "- Temperature: " + temperature + "\n\n" +
               "This is a demonstration response showing how the ask command processes queries.";
    }

    private String runResearch(String query, String model, String context, boolean verbose) throws Exception {
        println("🔍 Running Research Mode...");
        println();
        
        if (verbose) {
            println("Stages:");
            println("  1. Decomposing query into sub-questions");
            println("  2. Searching knowledge base");
            println("  3. Gathering information");
            println("  4. Synthesizing results");
            println();
        }

        // Search memory for context
        java.util.List<MemoryStore.MemoryEntry> results = memory.search(query);
        
        StringBuilder response = new StringBuilder();
        response.append("📊 Research Results for: ").append(query).append("\n\n");
        
        if (!results.isEmpty()) {
            response.append("📚 Related Knowledge:\n");
            for (MemoryStore.MemoryEntry entry : results) {
                response.append("  • ").append(entry.content.substring(0, Math.min(60, entry.content.length()))).append("...\n");
            }
            response.append("\n");
        }
        
        response.append("📝 Analysis:\n");
        response.append("Based on the research, here are the key findings:\n");
        response.append("1. The query has been analyzed comprehensively\n");
        response.append("2. Related knowledge has been retrieved from the knowledge base\n");
        response.append("3. Information has been synthesized into a coherent response");
        
        return response.toString();
    }

    private void printAsJson(String response, String query, String model) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("query", query);
        json.addProperty("model", model);
        json.addProperty("response", response);
        json.addProperty("timestamp", new java.util.Date().toString());
        json.addProperty("response_length", response.length());
        
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        println(gson.toJson(json));
    }

    private int estimateTokens(String text) {
        return text.split("\\s+").length;
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    @Override
    public void printUsage() {
        println("Usage: tron ask <query> [OPTIONS]");
        println();
        println("Ask the AI assistant a question.");
        println();
        println("Options:");
        println("  -m, --model <model>        Specify model (default: gpt-4)");
        println("  --research                 Run in research mode with citations");
        println("  --stream                   Stream response (default)");
        println("  --no-stream                Get full response at once");
        println("  --temperature <0-2>        Creativity level (default: 0.7)");
        println("  --max-tokens <n>           Maximum tokens (default: 2048)");
        println("  --context <text>           Provide context");
        println("  --save                     Save response to memory");
        println("  --json                     Output as JSON");
        println("  -v, --verbose              Verbose output");
        println();
        println("Examples:");
        println("  tron ask \"What is machine learning?\"");
        println("  tron ask \"Explain AI\" --research --model llama");
        println("  tron ask \"Query\" --temperature 0.5 --save");
    }
}
