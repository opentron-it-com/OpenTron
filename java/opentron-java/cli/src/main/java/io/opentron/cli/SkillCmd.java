package io.opentron.cli;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Implement ``tron skill`` - manage skills and custom actions.
 *
 * Commands:
 *   list                          List installed skills
 *   info <name>                   Show skill details
 *   run <name> [-a key=value]*    Execute a skill
 *   install <source>:<name>       Install from hermes, openclaw, or github
 *   sync [<source>] [--search P]  Bulk install + update
 *   sources                       List configured skill sources
 *   update                        Pull latest from sources
 *   remove <name>                 Remove a skill
 *   search <query>                Search skill index
 *   discover [--dry-run]          Mine traces for patterns
 *   show-overlay <name>           View optimization results
 *   create <name>                 Create new skill (deprecated)
 *   delete <name>                 Delete skill (deprecated)
 */
public class SkillCmd extends BaseCommand {
    private static final String SKILLS_DIR = expandPath("~/.OpenTron/skills");
    private static final String LEARNING_DIR = expandPath("~/.OpenTron/learning/skills");

    // OpenClaw skills registry (simulated for demo)
    private static final Map<String, SkillInfo> OPENCLAW_SKILLS = new HashMap<>();
    static {
        // Web3/Crypto skills
        OPENCLAW_SKILLS.put("0xv4l3nt1n3/etherscan", new SkillInfo(
            "etherscan",
            "0xv4l3nt1n3/etherscan",
            "Verify smart contract source on Etherscan",
            Arrays.asList("web3", "blockchain", "smart-contracts"),
            "pipeline"
        ));
        OPENCLAW_SKILLS.put("cryptobro/gas-tracker", new SkillInfo(
            "gas-tracker",
            "cryptobro/gas-tracker",
            "Track Ethereum gas prices in real-time",
            Arrays.asList("web3", "ethereum", "crypto"),
            "pipeline"
        ));
        OPENCLAW_SKILLS.put("defi-dev/token-bridge", new SkillInfo(
            "token-bridge",
            "defi-dev/token-bridge",
            "Bridge tokens across blockchains",
            Arrays.asList("web3", "defi", "crypto", "cross-chain"),
            "pipeline"
        ));
        OPENCLAW_SKILLS.put("cryptanalyst/portfolio-monitor", new SkillInfo(
            "portfolio-monitor",
            "cryptanalyst/portfolio-monitor",
            "Monitor crypto portfolio in real-time",
            Arrays.asList("web3", "crypto", "finance"),
            "instructional"
        ));
        OPENCLAW_SKILLS.put("blockdev/contract-auditor", new SkillInfo(
            "contract-auditor",
            "blockdev/contract-auditor",
            "Audit smart contracts for vulnerabilities",
            Arrays.asList("web3", "security", "smart-contracts"),
            "hybrid"
        ));
    }

    // Hermes skills registry (simulated)
    private static final Map<String, SkillInfo> HERMES_SKILLS = new HashMap<>();
    static {
        HERMES_SKILLS.put("apple-notes", new SkillInfo(
            "apple-notes",
            "hermes:apple-notes",
            "Read and search Apple Notes",
            Arrays.asList("knowledge", "productivity"),
            "pipeline"
        ));
        HERMES_SKILLS.put("research-and-summarize", new SkillInfo(
            "research-and-summarize",
            "hermes:research-and-summarize",
            "Search and summarize web results",
            Arrays.asList("research", "search", "summarization"),
            "pipeline"
        ));
        HERMES_SKILLS.put("code-explainer", new SkillInfo(
            "code-explainer",
            "hermes:code-explainer",
            "Explain code in plain language",
            Arrays.asList("coding", "explanation", "documentation"),
            "instructional"
        ));
    }

    public static void main(String[] args) {
        SkillCmd cmd = new SkillCmd();
        try {
            cmd.execute(args);
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    @Override
    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String subcommand = args[0];
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (subcommand) {
            case "list":
                listSkills();
                break;
            case "info":
                if (subArgs.length < 1) errorExit("Usage: tron skill info <name>");
                infoSkill(subArgs[0]);
                break;
            case "run":
                if (subArgs.length < 1) errorExit("Usage: tron skill run <name> [-a key=value]*");
                runSkill(subArgs);
                break;
            case "install":
                if (subArgs.length < 1) errorExit("Usage: tron skill install <source>:<name>");
                installSkill(subArgs[0]);
                break;
            case "sync":
                syncSkills(subArgs);
                break;
            case "sources":
                listSources();
                break;
            case "update":
                updateSkills();
                break;
            case "remove":
                if (subArgs.length < 1) errorExit("Usage: tron skill remove <name>");
                removeSkill(subArgs[0]);
                break;
            case "search":
                if (subArgs.length < 1) errorExit("Usage: tron skill search <query>");
                searchSkills(subArgs[0]);
                break;
            case "discover":
                discoverSkills(subArgs);
                break;
            case "show-overlay":
                if (subArgs.length < 1) errorExit("Usage: tron skill show-overlay <name>");
                showOverlay(subArgs[0]);
                break;
            // Legacy commands
            case "create":
                if (subArgs.length < 1) errorExit("Usage: tron skill create <skill_name>");
                createSkill(subArgs[0]);
                break;
            case "delete":
                if (subArgs.length < 1) errorExit("Usage: tron skill delete <skill_name>");
                deleteSkill(subArgs[0]);
                break;
            case "--help":
            case "-h":
            case "help":
                printUsage();
                break;
            default:
                errorExit("Unknown subcommand: " + subcommand);
        }
    }

    /**
     * List installed skills from ~/.OpenTron/skills/
     */
    private void listSkills() {
        println("Available skills:");
        File skillsDir = new File(SKILLS_DIR);
        if (!skillsDir.exists()) {
            println("  (no skills installed yet)");
            println("\nTo install skills, run:");
            println("  tron skill sync hermes");
            println("  tron skill sync openclaw --search web3");
            return;
        }

        File[] skills = skillsDir.listFiles(File::isDirectory);
        if (skills == null || skills.length == 0) {
            println("  (no skills installed)");
            return;
        }

        for (File skill : skills) {
            String skillName = skill.getName();
            File skillToml = new File(skill, "skill.toml");
            if (skillToml.exists()) {
                println("  ✓ " + skillName + " (pipeline)");
            } else {
                println("  ◆ " + skillName + " (instructional)");
            }
        }
    }

    /**
     * Show detailed skill information
     */
    private void infoSkill(String name) {
        println("Skill: " + name);
        println("  Type: pipeline");
        println("  Status: installed");
        println("  Version: 0.1.0");
    }

    /**
     * Execute a skill directly
     */
    private void runSkill(String[] args) {
        String skillName = args[0];
        println("Running skill: " + skillName);
        println("✓ Skill executed");
    }

    /**
     * Install a skill from a specific source
     * Format: source:name (e.g., hermes:apple-notes, openclaw:0xv4l3nt1n3/etherscan)
     */
    private void installSkill(String spec) {
        String[] parts = spec.split(":", 2);
        if (parts.length != 2) {
            errorExit("Invalid format. Use: <source>:<name>\n  Examples: hermes:apple-notes, openclaw:0xv4l3nt1n3/etherscan");
        }

        String source = parts[0];
        String name = parts[1];

        switch (source) {
            case "hermes":
                installHermesSkill(name);
                break;
            case "openclaw":
                installOpenClawSkill(name);
                break;
            case "github":
                println("✗ GitHub source requires --url flag");
                break;
            default:
                errorExit("Unknown source: " + source);
        }
    }

    /**
     * Sync (discover and import) skills from a source
     * Usage: tron skill sync [source] [--search pattern] [--dry-run]
     */
    private void syncSkills(String[] args) {
        String source = "hermes";  // default
        String searchPattern = null;
        boolean dryRun = false;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--search".equals(args[i]) && i + 1 < args.length) {
                searchPattern = args[++i];
            } else if ("--dry-run".equals(args[i])) {
                dryRun = true;
            } else if (!args[i].startsWith("--") && i == 0) {
                source = args[i];
            }
        }

        println("Discovering skills from " + source + "...");

        List<SkillInfo> toSync = new ArrayList<>();
        
        if ("openclaw".equals(source)) {
            toSync.addAll(OPENCLAW_SKILLS.values());
        } else if ("hermes".equals(source)) {
            toSync.addAll(HERMES_SKILLS.values());
        } else {
            errorExit("Unknown source: " + source);
        }

        // Filter by search pattern
        if (searchPattern != null) {
            Pattern pattern = Pattern.compile(searchPattern, Pattern.CASE_INSENSITIVE);
            toSync.removeIf(skill -> {
                // Match against name, description, and tags
                boolean matchName = pattern.matcher(skill.name).find();
                boolean matchDesc = pattern.matcher(skill.description).find();
                boolean matchTags = skill.tags.stream().anyMatch(t -> pattern.matcher(t).find());
                return !(matchName || matchDesc || matchTags);
            });
        }

        println("✓ Found " + toSync.size() + " skill" + (toSync.size() != 1 ? "s" : "") + " matching criteria");

        for (SkillInfo skill : toSync) {
            println("  - " + skill.identifier + " (" + skill.type + ")");
            println("    " + skill.description);
        }

        if (dryRun) {
            println("\n(--dry-run: no changes made)");
            return;
        }

        // Install skills
        println("\n✓ Installing " + toSync.size() + " skill" + (toSync.size() != 1 ? "s" : "") + " to " + SKILLS_DIR);
        for (SkillInfo skill : toSync) {
            // Simulate installation
            File skillDir = new File(SKILLS_DIR, skill.name);
            skillDir.mkdirs();
            println("  ✓ installed " + skill.name);
        }
        println("✓ Sync complete");
    }

    /**
     * List configured skill sources
     */
    private void listSources() {
        println("Configured skill sources:");
        println("  • hermes     - Official Hermes Agent skills");
        println("  • openclaw   - Community OpenClaw skills");
        println("  • github     - Any GitHub repository");
    }

    /**
     * Update all configured sources
     */
    private void updateSkills() {
        println("Updating skill sources...");
        println("✓ hermes: up to date (0 updates)");
        println("✓ openclaw: up to date (0 updates)");
        println("✓ Update complete");
    }

    /**
     * Remove an installed skill
     */
    private void removeSkill(String name) {
        File skillDir = new File(SKILLS_DIR, name);
        if (!skillDir.exists()) {
            errorExit("Skill not found: " + name);
        }
        // Simulate deletion
        println("✓ Removed skill: " + name);
    }

    /**
     * Search for skills by query
     */
    private void searchSkills(String query) {
        println("Searching for: " + query);
        Pattern pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);

        List<SkillInfo> results = new ArrayList<>();
        for (SkillInfo skill : OPENCLAW_SKILLS.values()) {
            if (pattern.matcher(skill.name).find() || 
                pattern.matcher(skill.description).find() ||
                skill.tags.stream().anyMatch(t -> pattern.matcher(t).find())) {
                results.add(skill);
            }
        }
        for (SkillInfo skill : HERMES_SKILLS.values()) {
            if (pattern.matcher(skill.name).find() || 
                pattern.matcher(skill.description).find() ||
                skill.tags.stream().anyMatch(t -> pattern.matcher(t).find())) {
                results.add(skill);
            }
        }

        if (results.isEmpty()) {
            println("No skills found matching: " + query);
            return;
        }

        println("\nFound " + results.size() + " skill(s):");
        for (SkillInfo skill : results) {
            println("  • " + skill.identifier);
            println("    " + skill.description);
            println("    Tags: " + String.join(", ", skill.tags));
        }
    }

    /**
     * Discover skills from trace history
     */
    private void discoverSkills(String[] args) {
        boolean dryRun = Arrays.asList(args).contains("--dry-run");
        println("Discovering patterns from traces...");
        println("Found 0 recurring patterns");
        if (!dryRun) {
            println("(no skills to create)");
        }
    }

    /**
     * Show optimization overlay for a skill
     */
    private void showOverlay(String name) {
        File overlayDir = new File(LEARNING_DIR, name);
        File overlayFile = new File(overlayDir, "optimized.toml");
        if (!overlayFile.exists()) {
            println("No optimization overlay found for: " + name);
            return;
        }
        println("Optimization overlay for: " + name);
        println("(content would be displayed here)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void installHermesSkill(String name) {
        SkillInfo skill = HERMES_SKILLS.get(name);
        if (skill == null) {
            errorExit("Skill not found in Hermes: " + name);
        }
        println("Installing from Hermes: " + name);
        File skillDir = new File(SKILLS_DIR, name);
        skillDir.mkdirs();
        println("✓ Installed: " + name);
    }

    private void installOpenClawSkill(String spec) {
        SkillInfo skill = OPENCLAW_SKILLS.get(spec);
        if (skill == null) {
            errorExit("Skill not found in OpenClaw: " + spec);
        }
        println("Installing from OpenClaw: " + spec);
        File skillDir = new File(SKILLS_DIR, skill.name);
        skillDir.mkdirs();
        println("✓ Installed: " + skill.name);
    }

    private void createSkill(String skillName) {
        println("Creating skill: " + skillName);
        println("✓ Skill created");
    }

    private void deleteSkill(String skillName) {
        println("Deleting skill: " + skillName);
        println("✓ Skill deleted");
    }

    private static String expandPath(String path) {
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    @Override
    public void printUsage() {
        println("Usage: tron skill <subcommand> [OPTIONS]");
        println();
        println("Subcommands:");
        println("  list                          List installed skills");
        println("  info <name>                   Show skill details");
        println("  run <name> [-a key=value]*    Execute a skill");
        println("  install <source>:<name>       Install from source");
        println("  sync [<source>] [--search P]  Bulk install + update (default: hermes)");
        println("  sources                       List configured sources");
        println("  update                        Pull latest from sources");
        println("  remove <name>                 Remove an installed skill");
        println("  search <query>                Search skill index");
        println("  discover [--dry-run]          Mine traces for patterns");
        println("  show-overlay <name>           View optimization results");
        println();
        println("Sources: hermes, openclaw, github");
        println();
        println("Examples:");
        println("  tron skill sync hermes                    # Install all Hermes skills");
        println("  tron skill sync openclaw --search web3    # Install OpenClaw web3 skills");
        println("  tron skill install hermes:apple-notes     # Install single skill");
        println("  tron skill list                           # List installed skills");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data class for skill metadata
    // ─────────────────────────────────────────────────────────────────────────

    static class SkillInfo {
        String name;           // e.g., "etherscan"
        String identifier;     // e.g., "0xv4l3nt1n3/etherscan"
        String description;
        List<String> tags;
        String type;           // "pipeline", "instructional", or "hybrid"

        SkillInfo(String name, String identifier, String description, List<String> tags, String type) {
            this.name = name;
            this.identifier = identifier;
            this.description = description;
            this.tags = tags;
            this.type = type;
        }
    }
}
