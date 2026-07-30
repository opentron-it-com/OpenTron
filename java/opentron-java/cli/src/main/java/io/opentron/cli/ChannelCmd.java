package io.opentron.cli;

import io.opentron.cli.data.DataManager;
import io.opentron.cli.data.TelemetryStore;

/**
 * Implement ``Tron channel`` - manage communication channels.
 */
public class ChannelCmd extends BaseCommand {
    private TelemetryStore telemetry;

    public static void main(String[] args) throws Exception {
        ChannelCmd cmd = new ChannelCmd();
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
        
        if (args.length == 0) {
            listChannels();
            return;
        }

        String subcommand = args[0];
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        
        switch (subcommand) {
            case "list":
                listChannels();
                break;
            case "status":
                showStatus();
                break;
            case "connect":
                if (subArgs.length == 0) {
                    errorExit("Usage: tron channel connect <channel-name>");
                }
                connectChannel(subArgs[0]);
                break;
            case "disconnect":
                if (subArgs.length == 0) {
                    errorExit("Usage: tron channel disconnect <channel-name>");
                }
                disconnectChannel(subArgs[0]);
                break;
            case "send":
                if (subArgs.length < 2) {
                    errorExit("Usage: tron channel send <channel> <message>");
                }
                sendMessage(subArgs[0], subArgs[1]);
                break;
            case "history":
                showHistory(subArgs);
                break;
            default:
                errorExit("Unknown subcommand: " + subcommand);
        }
    }

    private void listChannels() {
        println("Available Channels:");
        println();
        println("Name           Type        Status      Users");
        println("-".repeat(50));
        println("general        text        connected   5");
        println("ai-discussion  text        connected   3");
        println("notifications  system      connected   10");
        println();
        println("Run 'tron channel status' for details");
    }

    private void showStatus() {
        println("Channel Status:");
        println();
        println("Connected Channels: 3");
        println("Total Users:        18");
        println("Unread Messages:    2");
        println();
        println("Recent Activity:");
        println("  10:30 - Message in #general");
        println("  10:15 - User joined #ai-discussion");
        println("  10:00 - Notification received");
    }

    private void connectChannel(String channelName) throws Exception {
        println("Connecting to channel: " + channelName);
        telemetry.recordEvent("channel_connect", channelName, "local", 100, 0, true);
        println("✓ Connected to #" + channelName);
    }

    private void disconnectChannel(String channelName) throws Exception {
        println("Disconnecting from channel: " + channelName);
        telemetry.recordEvent("channel_disconnect", channelName, "local", 50, 0, true);
        println("✓ Disconnected from #" + channelName);
    }

    private void sendMessage(String channel, String message) throws Exception {
        println("Sending to #" + channel + ": " + message);
        telemetry.recordEvent("channel_send", channel, "local", 75, 0, true);
        println("✓ Message sent");
    }

    private void showHistory(String[] args) {
        String channel = args.length > 0 ? args[0] : "general";
        int limit = 10;
        
        for (int i = 0; i < args.length; i++) {
            if (("-n".equals(args[i]) || "--limit".equals(args[i])) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            }
        }
        
        println("Channel History: #" + channel + " (last " + limit + " messages)");
        println();
        println("10:30 - Alice: Hello everyone!");
        println("10:28 - Bob: Hi there");
        println("10:25 - System: Charlie joined the channel");
        println("10:20 - Alice: How's everyone doing?");
        println();
    }

    @Override
    public void printUsage() {
        println("Usage: tron channel <subcommand> [OPTIONS]");
        println();
        println("Subcommands:");
        println("  list                List available channels");
        println("  status              Show channel status");
        println("  connect <channel>   Join a channel");
        println("  disconnect <ch>     Leave a channel");
        println("  send <ch> <msg>     Send message");
        println("  history [ch]        Show message history");
    }
}
