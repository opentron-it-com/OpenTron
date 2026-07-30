package io.opentron.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.opentron.core.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ChatCmd {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String currentModel = null;
    private static String currentEngine = "ollama";
    private static List<Message> conversationHistory = new ArrayList<>();

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        String engineKey = null;
        String modelName = null;
        String agentName = null;
        String toolsStr = null;
        String systemPrompt = null;
        String personaName = null;

        // Parse options
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (("-e".equals(arg) || "--engine".equals(arg)) && i + 1 < args.length) {
                engineKey = args[++i];
            } else if (("-m".equals(arg) || "--model".equals(arg)) && i + 1 < args.length) {
                modelName = args[++i];
            } else if (("-a".equals(arg) || "--agent".equals(arg)) && i + 1 < args.length) {
                agentName = args[++i];
            } else if ("--tools".equals(arg) && i + 1 < args.length) {
                toolsStr = args[++i];
            } else if ("--system".equals(arg) && i + 1 < args.length) {
                systemPrompt = args[++i];
            } else if ("--persona".equals(arg) && i + 1 < args.length) {
                personaName = args[++i];
            }
        }

        if (engineKey != null) {
            currentEngine = engineKey;
        }

        try {
            // Get available models
            String modelsResponse = Utils.httpGet("/v1/models");
            JsonObject modelsJson = GSON.fromJson(modelsResponse, JsonObject.class);
            JsonArray models = modelsJson.getAsJsonArray("data");

            if (models == null || models.size() == 0) {
                System.err.println("No models available. Run `Tron init` and pull a model.");
                System.exit(1);
            }

            // Use specified model or first available
            if (modelName != null) {
                currentModel = modelName;
            } else {
                currentModel = models.get(0).getAsJsonObject().get("id").getAsString();
            }

            // Print banner
            System.out.println("\n╭─────────────────────────────────────╮");
            System.out.println("│   OpenTron Interactive Chat          │");
            System.out.println("╰─────────────────────────────────────╯\n");
            System.out.println("  Engine: " + currentEngine);
            System.out.println("  Model: " + currentModel);
            if (agentName != null) {
                System.out.println("  Agent: " + agentName);
            }
            System.out.println("  Type /help for commands, /quit to exit.\n");

            // Initialize system prompt if provided
            if (systemPrompt != null) {
                conversationHistory.add(new Message("system", systemPrompt));
            }

            // REPL loop
            startREPL();
        } catch (Exception e) {
            System.err.println("Error starting chat: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void startREPL() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("You> ");
            String userInput = reader.readLine();

            if (userInput == null) {
                // EOF
                System.out.println("\nExit.");
                break;
            }

            userInput = userInput.trim();
            if (userInput.isEmpty()) {
                continue;
            }

            // Handle special commands
            if (userInput.startsWith("/")) {
                if (!handleCommand(userInput)) {
                    break;
                }
                continue;
            }

            // Send to chat API
            try {
                String response = sendChatMessage(userInput);
                if (response != null) {
                    System.out.println("\nAssistant> " + response + "\n");
                    conversationHistory.add(new Message("assistant", response));
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private static boolean handleCommand(String command) {
        if ("/quit".equals(command) || "/exit".equals(command)) {
            System.out.println("Exit.");
            return false;
        } else if ("/clear".equals(command)) {
            conversationHistory.clear();
            System.out.println("Conversation history cleared.\n");
            return true;
        } else if ("/model".equals(command)) {
            System.out.println("Current model: " + currentModel + "\n");
            return true;
        } else if ("/history".equals(command)) {
            System.out.println("\n--- Conversation History ---");
            for (Message msg : conversationHistory) {
                System.out.println("[" + msg.role + "] " + msg.content);
            }
            System.out.println("--- End History ---\n");
            return true;
        } else if ("/help".equals(command)) {
            printHelp();
            return true;
        } else {
            System.out.println("Unknown command: " + command);
            System.out.println("Type /help for available commands.\n");
            return true;
        }
    }

    private static String sendChatMessage(String userMessage) throws Exception {
        // Add user message to history
        conversationHistory.add(new Message("user", userMessage));

        // Build request
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", currentModel);
        requestBody.addProperty("stream", false);

        // Add conversation history as messages
        JsonArray messagesArray = new JsonArray();
        for (Message msg : conversationHistory) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role);
            msgObj.addProperty("content", msg.content);
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);

        // Send request
        String response = Utils.httpPost("/v1/chat/completions", GSON.toJson(requestBody));
        JsonObject result = GSON.fromJson(response, JsonObject.class);

        try {
            JsonArray choices = result.getAsJsonArray("choices");
            if (choices != null && choices.size() > 0) {
                String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content")
                        .getAsString();
                return content;
            }
        } catch (Exception e) {
            System.err.println("Failed to parse response: " + e.getMessage());
        }

        return "No response received.";
    }

    private static void printHelp() {
        System.out.println("\n--- Chat Commands ---");
        System.out.println("/quit, /exit     Exit the chat session");
        System.out.println("/clear           Clear conversation history");
        System.out.println("/model           Show current model");
        System.out.println("/history         Show conversation history");
        System.out.println("/help            Show this help message\n");
    }

    static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}

