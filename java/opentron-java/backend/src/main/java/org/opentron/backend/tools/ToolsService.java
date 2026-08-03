package org.opentron.backend.tools;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ToolsService {

    private final TavilyTool tavilyTool;

    public ToolsService(TavilyTool tavilyTool) {
        this.tavilyTool = tavilyTool;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                Map.ofEntries(
                        Map.entry("name", "browser"),
                        Map.entry("description", "Browser automation tool"),
                        Map.entry("category", "tool"),
                        Map.entry("source", "builtin"),
                        Map.entry("configured", true),
                        Map.entry("requires_credentials", false),
                        Map.entry("credential_keys", List.of()),
                        Map.entry("documentation_url", "https://docs.opentron.ai/tools/browser"),
                        Map.entry("examples", List.of(
                                "browser navigate url=https://example.com",
                                "browser click selector=#submit"
                        )),
                        Map.entry("parameters", Map.of(
                                "action", Map.of("type", "string", "description", "Action to perform: click, type, screenshot"),
                                "url", Map.of("type", "string", "description", "Target URL to navigate to")
                        )),
                        Map.entry("capabilities", List.of("navigate", "click", "read", "screenshot"))
                ),
                Map.ofEntries(
                        Map.entry("name", "slack"),
                        Map.entry("description", "Slack channel integration"),
                        Map.entry("category", "channel"),
                        Map.entry("source", "integration"),
                        Map.entry("configured", false),
                        Map.entry("requires_credentials", true),
                        Map.entry("credential_keys", List.of("slack_token")),
                        Map.entry("credential_help", "Create a Slack bot token and add it as slack_token in credentials."),
                        Map.entry("documentation_url", "https://docs.opentron.ai/tools/slack"),
                        Map.entry("examples", List.of(
                                "slack channel=#general text=Hello from OpenTron",
                                "slack channel=#alerts text=Deployment completed"
                        )),
                        Map.entry("parameters", Map.of(
                                "channel", Map.of("type", "string", "description", "Slack channel to post to"),
                                "text", Map.of("type", "string", "description", "Message text")
                        )),
                        Map.entry("capabilities", List.of("send_message", "fetch_history"))
                ),
                Map.of(
                        "name", "telegram",
                        "description", "Telegram channel integration",
                        "category", "channel",
                        "source", "integration",
                        "configured", false,
                        "requires_credentials", true,
                        "credential_keys", List.of("telegram_token"),
                        "credential_help", "Use a Telegram bot token and configure it as telegram_token.",
                        "documentation_url", "https://docs.opentron.ai/tools/telegram",
                        "capabilities", List.of("send_message")
                ),
                Map.of(
                        "name", "discord",
                        "description", "Discord channel integration",
                        "category", "channel",
                        "source", "integration",
                        "configured", false,
                        "requires_credentials", true,
                        "credential_keys", List.of("discord_token"),
                        "credential_help", "Set up a Discord bot token and add it as discord_token.",
                        "documentation_url", "https://docs.opentron.ai/tools/discord",
                        "capabilities", List.of("send_message")
                ),
                Map.ofEntries(
                        Map.entry("name", "email"),
                        Map.entry("description", "Email channel integration"),
                        Map.entry("category", "channel"),
                        Map.entry("source", "integration"),
                        Map.entry("configured", false),
                        Map.entry("requires_credentials", true),
                        Map.entry("credential_keys", List.of("smtp_server", "smtp_username", "smtp_password")),
                        Map.entry("credential_help", "Provide SMTP credentials in the credential store under smtp_server, smtp_username, and smtp_password."),
                        Map.entry("documentation_url", "https://docs.opentron.ai/tools/email"),
                        Map.entry("examples", List.of(
                                "email recipient=ops@example.com subject=Alert body=Workflow completed"
                        )),
                        Map.entry("parameters", Map.of(
                                "recipient", Map.of("type", "string", "description", "Email recipient address"),
                                "subject", Map.of("type", "string", "description", "Email subject line"),
                                "body", Map.of("type", "string", "description", "Email body content")
                        )),
                        Map.entry("capabilities", List.of("send_email"))
                ),
                tavilyTool.getToolDefinition()
        );
    }

    public Map<String, Object> getTool(String name) {
        return listTools().stream()
                .filter(tool -> name.equals(tool.get("name")))
                .findFirst()
                .orElse(null);
    }
}
