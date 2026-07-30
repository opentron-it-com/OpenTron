package io.opentron.cli;

import java.util.*;

/**
 * Main CLI dispatcher for OpenTron.
 * Routes commands to appropriate handlers (Java-native implementations).
 */
public class Main {
    private static final String VERSION = "0.1.0";
    private static final String PROGRAM_NAME = "Tron";

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                printUsage();
                System.exit(0);
            }

            String command = args[0];
            String[] commandArgs = args.length > 1 ? 
                Arrays.copyOfRange(args, 1, args.length) : new String[0];

            // Handle version flags
            if ("--version".equals(command) || "-V".equals(command) || "version".equals(command)) {
                System.out.println(PROGRAM_NAME + ", version " + VERSION);
                System.exit(0);
            }

            // Handle help
            if ("--help".equals(command) || "-h".equals(command) || "help".equals(command)) {
                printUsage();
                System.exit(0);
            }

            // Voice mode activation
            if ("--voice".equals(command) || "voice".equals(command)) {
                VoiceCmd.main(commandArgs);
                System.exit(0);
            }

            // Route to command handlers
            switch (command) {
                // Core commands
                case "serve":
                    Serve.main(commandArgs);
                    return;
                case "chat":
                    ChatCmd.main(commandArgs);
                    return;
                case "ask":
                    Ask.main(commandArgs);
                    return;
                case "agent":
                case "agents":
                    AgentCmd.main(commandArgs);
                    return;
                case "init":
                    InitCmd.main(commandArgs);
                    return;
                case "doctor":
                    DoctorCmd.main(commandArgs);
                    return;
                case "config":
                    ConfigCmd.main(commandArgs);
                    return;
                case "auth":
                    AuthCmd.main(commandArgs);
                    return;
                case "model":
                    ModelCmd.main(commandArgs);
                    return;
                case "memory":
                    MemoryCmd.main(commandArgs);
                    return;
                case "tool":
                    ToolCmd.main(commandArgs);
                    return;
                case "skill":
                    SkillCmd.main(commandArgs);
                    return;
                case "workflow":
                    WorkflowCmd.main(commandArgs);
                    return;
                case "learning":
                    LearningCmd.main(commandArgs);
                    return;
                case "traces":
                    TracesCmd.main(commandArgs);
                    return;
                case "vault":
                    VaultCmd.main(commandArgs);
                    return;
                case "channels":
                    ChannelsCmd.main(commandArgs);
                    return;
                case "channel":
                    ChannelCmd.main(commandArgs);
                    return;
                case "scan":
                    ScanCmd.main(commandArgs);
                    return;
                case "daemon":
                    DaemonCmd.main(commandArgs);
                    return;
                case "connect":
                    ConnectCmd.main(commandArgs);
                    return;
                case "add":
                    AddCmd.main(commandArgs);
                    return;
                case "bench":
                    BenchCmd.main(commandArgs);
                    return;
                case "bootstrap":
                    Bootstrap.main(commandArgs);
                    return;
                case "compose":
                    ComposeCmd.main(commandArgs);
                    return;
                case "digest":
                    DigestCmd.main(commandArgs);
                    return;
                case "eval":
                    EvalCmd.main(commandArgs);
                    return;
                case "feedback":
                    FeedbackCmd.main(commandArgs);
                    return;
                case "gateway":
                    GatewayCmd.main(commandArgs);
                    return;
                case "host":
                    HostCmd.main(commandArgs);
                    return;
                case "mine":
                    MineCmd.main(commandArgs);
                    return;
                case "operators":
                    OperatorsCmd.main(commandArgs);
                    return;
                case "optimize":
                    OptimizeCmd.main(commandArgs);
                    return;
                case "pearl":
                    PearlCmd.main(commandArgs);
                    return;
                case "quickstart":
                    QuickstartCmd.main(commandArgs);
                    return;
                case "registry":
                    RegistryCmd.main(commandArgs);
                    return;
                case "scheduler":
                    SchedulerCmd.main(commandArgs);
                    return;
                case "self-update":
                    SelfUpdateCmd.main(commandArgs);
                    return;
                case "telemetry":
                    TelemetryCmd.main(commandArgs);
                    return;
                case "tunnel":
                    TunnelCmd.main(commandArgs);
                    return;
                case "voice":
                    VoiceCmd.main(commandArgs);
                    return;
                default:
                    System.err.println("✗ Unknown command: " + command);
                    System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println(Banner.getMainBanner());
        System.out.println("Usage: tron <command> [options]");
        System.out.println("\nCore commands: init config auth doctor chat ask");
        System.out.println("Agent commands: agent memory learning");
        System.out.println("Integration: tool skill workflow vault channels");
        System.out.println("System: serve daemon scan");
        System.out.println("\nSpecial flags: --version  --help  --voice");
        System.out.println("\nRun: tron <command> --help for details");
    }
}
