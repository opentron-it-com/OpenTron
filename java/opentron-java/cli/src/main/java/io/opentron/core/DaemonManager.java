package io.opentron.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;

/**
 * Manages the OpenTron server daemon lifecycle (start, stop, status, restart).
 * Handles process spawning, PID file management, and graceful shutdown.
 */
public class DaemonManager {
    private static final String CONFIG_DIR_ENV = "OPENTRON_CONFIG_DIR";
    private static final String DEFAULT_CONFIG_DIR = ".openjarvis";
    private static final String PID_FILENAME = "server.pid";
    private static final String LOG_FILENAME = "server.log";
    private static final long SIGTERM_TIMEOUT_SECONDS = 10;

    private final Path configDir;
    private final Path pidFile;
    private final Path logFile;

    public DaemonManager() {
        // Determine config directory
        String configDirEnv = System.getenv(CONFIG_DIR_ENV);
        if (configDirEnv != null && !configDirEnv.isEmpty()) {
            this.configDir = Paths.get(configDirEnv);
        } else {
            this.configDir = Paths.get(System.getProperty("user.home"), DEFAULT_CONFIG_DIR);
        }

        this.pidFile = configDir.resolve(PID_FILENAME);
        this.logFile = configDir.resolve(LOG_FILENAME);

        // Ensure config directory exists
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            System.err.println("Warning: Failed to create config directory: " + e.getMessage());
        }
    }

    /**
     * Start the OpenTron server as a background daemon process.
     */
    public void start(String[] serverArgs) {
        if (isRunning()) {
            System.out.println("Server is already running (PID: " + readPid() + ")");
            return;
        }

        try {
            // Build command: java -jar tron-cli-jar-with-dependencies.jar serve [args]
            ProcessBuilder pb = new ProcessBuilder();
            
            // Find Java executable
            String javaExe = System.getProperty("java.home");
            if (javaExe == null || javaExe.isEmpty()) {
                System.err.println("Error: JAVA_HOME not set");
                System.exit(1);
            }
            
            File javaExePath = new File(javaExe, "bin" + File.separator + "java");
            if (!javaExePath.exists()) {
                javaExePath = new File(javaExe, "bin" + File.separator + "java.exe");
            }

            // Find the JAR file
            String jarPath = findTronJar();
            if (jarPath == null) {
                System.err.println("Error: Could not find tron CLI JAR file");
                System.exit(1);
            }

            // Build command line
            pb.command(javaExePath.getAbsolutePath(), "-jar", jarPath, "serve");
            
            // Add server arguments if provided
            if (serverArgs != null && serverArgs.length > 0) {
                for (String arg : serverArgs) {
                    pb.command().add(arg);
                }
            }

            // Redirect output to log file
            pb.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
            pb.redirectErrorStream(true);

            // Start process (non-blocking)
            Process process = pb.start();

            // Get process ID
            long pid = getPidFromProcess(process);
            if (pid > 0) {
                writePid(pid);
                System.out.println("Server started (PID: " + pid + ")");
                System.out.println("Log file: " + logFile);
            } else {
                System.err.println("Error: Could not determine process ID");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Stop the running server gracefully, then forcefully if needed.
     */
    public void stop() {
        long pid = readPid();
        if (pid <= 0) {
            System.out.println("Server is not running");
            return;
        }

        try {
            // Try graceful shutdown with SIGTERM
            System.out.println("Stopping server (PID: " + pid + ")...");
            
            // Use OS command to send SIGTERM
            ProcessBuilder pb = buildSignalCommand(pid, "-15"); // SIGTERM
            pb.start().waitFor();

            // Wait for process to terminate
            long startTime = System.currentTimeMillis();
            while (isProcessRunning(pid) && 
                   System.currentTimeMillis() - startTime < SIGTERM_TIMEOUT_SECONDS * 1000) {
                Thread.sleep(500);
            }

            // Force kill if still running
            if (isProcessRunning(pid)) {
                System.out.println("Process did not stop gracefully, sending SIGKILL...");
                ProcessBuilder killPb = buildSignalCommand(pid, "-9"); // SIGKILL
                killPb.start().waitFor();
                Thread.sleep(500);
            }

            if (!isProcessRunning(pid)) {
                deletePid();
                System.out.println("Server stopped");
            } else {
                System.err.println("Error: Failed to stop server");
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error stopping server: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Restart the server (stop then start).
     */
    public void restart(String[] serverArgs) {
        if (isRunning()) {
            stop();
            try {
                Thread.sleep(1000); // Wait a bit before restarting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        start(serverArgs);
    }

    /**
     * Show server status.
     */
    public void status() {
        long pid = readPid();
        
        if (!isRunning()) {
            System.out.println("Server is not running");
            return;
        }

        System.out.println("Server is running (PID: " + pid + ")");
        
        // Try to show uptime
        try {
            long startTime = Files.getLastModifiedTime(pidFile).toMillis();
            Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startTime);
            long hours = uptime.toHours();
            long minutes = uptime.toMinutesPart();
            long seconds = uptime.toSecondsPart();
            System.out.println("Uptime: " + hours + "h " + minutes + "m " + seconds + "s");
        } catch (IOException e) {
            // Ignore if we can't read uptime
        }

        System.out.println("Log file: " + logFile);
    }

    /**
     * Check if server is currently running.
     */
    private boolean isRunning() {
        long pid = readPid();
        return pid > 0 && isProcessRunning(pid);
    }

    /**
     * Check if a process with given PID is running.
     */
    private boolean isProcessRunning(long pid) {
        try {
            if (isWindows()) {
                // Windows: use tasklist
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid);
                Process p = pb.start();
                try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A")) {
                    String result = s.hasNext() ? s.next() : "";
                    p.waitFor();
                    return result.contains(String.valueOf(pid));
                }
            } else {
                // Unix: use ps
                ProcessBuilder pb = new ProcessBuilder("ps", "-p", String.valueOf(pid));
                Process p = pb.start();
                int exitCode = p.waitFor();
                return exitCode == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Read PID from file.
     */
    private long readPid() {
        try {
            if (Files.exists(pidFile)) {
                String content = Files.readString(pidFile).trim();
                return Long.parseLong(content);
            }
        } catch (IOException | NumberFormatException e) {
            // Ignore read errors
        }
        return -1;
    }

    /**
     * Write PID to file.
     */
    private void writePid(long pid) {
        try {
            Files.writeString(pidFile, String.valueOf(pid));
        } catch (IOException e) {
            System.err.println("Warning: Failed to write PID file: " + e.getMessage());
        }
    }

    /**
     * Delete PID file.
     */
    private void deletePid() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            // Ignore deletion errors
        }
    }

    /**
     * Get process ID from a Process object (works on Java 9+).
     */
    private long getPidFromProcess(Process process) {
        try {
            // Java 9+ has ProcessHandle
            return process.pid();
        } catch (Exception e) {
            // Fallback for older Java versions
            return -1;
        }
    }

    /**
     * Find the tron CLI JAR file.
     */
    private String findTronJar() {
        // Check environment variable first
        String jarEnv = System.getenv("TRON_JAVA_JAR");
        if (jarEnv != null && new File(jarEnv).exists()) {
            return jarEnv;
        }

        // Try to find it in the classpath or installation directory
        // This is a simplified search - in production, might be more complex
        File currentJar = new File("tron-cli-jar-with-dependencies.jar");
        if (currentJar.exists()) {
            return currentJar.getAbsolutePath();
        }

        // Search in parent directories
        File searchDir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 5; i++) {
            File jar = new File(searchDir, "tron-cli-jar-with-dependencies.jar");
            if (jar.exists()) {
                return jar.getAbsolutePath();
            }
            searchDir = searchDir.getParentFile();
            if (searchDir == null) break;
        }

        return null;
    }

    /**
     * Build platform-specific signal command.
     */
    private ProcessBuilder buildSignalCommand(long pid, String signal) {
        if (isWindows()) {
            // Windows: use taskkill
            return new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), 
                                     signal.equals("-9") ? "/F" : "");
        } else {
            // Unix: use kill
            return new ProcessBuilder("kill", signal, String.valueOf(pid));
        }
    }

    /**
     * Check if running on Windows.
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
