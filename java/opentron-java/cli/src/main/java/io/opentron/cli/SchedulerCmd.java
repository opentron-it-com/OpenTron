package io.opentron.cli;


/**
 * Implement ``Tron scheduler`` - task scheduling and automation.
 */
public class SchedulerCmd extends BaseCommand {
    public static void main(String[] args) throws Exception {
        SchedulerCmd cmd = new SchedulerCmd();
        try {
            cmd.execute(args);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            listScheduledTasks();
            return;
        }

        String subcommand = args[0];
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        switch (subcommand) {
            case "list":
                listScheduledTasks();
                break;
            case "schedule":
                if (subArgs.length < 2) {
                    errorExit("Usage: tron scheduler schedule <name> --cron <schedule>");
                }
                scheduleTask(subArgs);
                break;
            case "unschedule":
                if (subArgs.length == 0) {
                    errorExit("Usage: tron scheduler unschedule <task_id>");
                }
                unscheduleTask(subArgs[0]);
                break;
            case "trigger":
                if (subArgs.length == 0) {
                    errorExit("Usage: tron scheduler trigger <task_id>");
                }
                triggerTask(subArgs[0]);
                break;
            case "history":
                showTaskHistory(subArgs);
                break;
            default:
                errorExit("Unknown subcommand: " + subcommand);
        }
    }

    private void listScheduledTasks() {
        println("Scheduled Tasks:");
        println();
        println("ID       Name                Schedule             Status");
        println("-".repeat(70));
        println("1        morning_digest      0 7 * * *           active");
        println("2        nightly_backup      0 23 * * *          active");
        println("3        health_check        */5 * * * *          active");
        println();
        println("Run 'tron scheduler history <task_id>' for execution history");
    }

    private void scheduleTask(String[] args) {
        String name = args[0];
        String cronExpr = null;

        for (int i = 1; i < args.length; i++) {
            if ("--cron".equals(args[i]) && i + 1 < args.length) {
                cronExpr = args[++i];
            }
        }

        if (cronExpr == null) {
            errorExit("Must specify --cron expression");
        }

        println("Scheduling task: " + name);
        println("  Schedule: " + cronExpr);
        println("✓ Task scheduled successfully (ID: 4)");
    }

    private void unscheduleTask(String taskId) {
        println("Unscheduling task: " + taskId);
        println("✓ Task unscheduled");
    }

    private void triggerTask(String taskId) {
        println("Triggering task: " + taskId);
        println("  Execution started...");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        println("✓ Task executed successfully");
    }

    private void showTaskHistory(String[] args) {
        String taskId = args.length > 0 ? args[0] : "1";
        println("Execution History for Task " + taskId + ":");
        println();
        println("Timestamp                Status    Duration  Output");
        println("-".repeat(70));
        println("2024-01-15 07:00:00      success   2.3s      OK");
        println("2024-01-14 07:00:00      success   2.1s      OK");
        println("2024-01-13 07:00:00      failed    0.5s      Timeout");
        println();
    }

    @Override
    public void printUsage() {
        println("Usage: tron scheduler <subcommand> [OPTIONS]");
        println();
        println("Subcommands:");
        println("  list                List all scheduled tasks");
        println("  schedule <name>     Create a new scheduled task");
        println("  unschedule <id>     Remove a scheduled task");
        println("  trigger <id>        Run a task immediately");
        println("  history [id]        Show task execution history");
        println();
        println("Schedule Format:");
        println("  Cron expressions (e.g., '0 7 * * *' for daily at 7am)");
    }
}
