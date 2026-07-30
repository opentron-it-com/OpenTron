package org.opentron.backend.storage.service;

import org.opentron.backend.storage.entities.AgentMemory;
import org.opentron.backend.storage.entities.TraceLog;
import org.opentron.backend.storage.repositories.AgentMemoryRepository;
import org.opentron.backend.storage.repositories.TraceLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Main storage service for OpenTron
 * Handles all database operations for traces, memory, and metadata
 */
@Service
@Transactional
public class StorageService {
    
    private final AgentMemoryRepository agentMemoryRepo;
    private final TraceLogRepository traceLogRepo;
    @SuppressWarnings("unused")
    private final CompressionService compressionService;
    
    public StorageService(AgentMemoryRepository agentMemoryRepo,
                         TraceLogRepository traceLogRepo,
                         CompressionService compressionService) {
        this.agentMemoryRepo = agentMemoryRepo;
        this.traceLogRepo = traceLogRepo;
        this.compressionService = compressionService;
    }
    
    // ============ Agent Memory Methods ============
    
    /**
     * Save agent memory with automatic deduplication
     */
    public AgentMemory saveAgentMemory(String agentName, String rawTrace, String compressedSummary) {
        String traceHash = computeSHA256(rawTrace);
        
        // Check for duplicates
        Optional<AgentMemory> existing = agentMemoryRepo.findByTraceHash(traceHash);
        if (existing.isPresent()) {
            org.slf4j.LoggerFactory.getLogger(StorageService.class).info("Duplicate trace detected for {}, skipping", agentName);
            return existing.get();
        }
        
        AgentMemory memory = new AgentMemory(agentName, rawTrace, compressedSummary);
        memory.setTraceHash(traceHash);
        
        AgentMemory saved = agentMemoryRepo.save(memory);
        org.slf4j.LoggerFactory.getLogger(StorageService.class).info("Saved memory for {} (ID: {})", agentName, saved.getId());
        return saved;
    }
    
    /**
     * Load recent memory for an agent
     */
    public List<AgentMemory> loadAgentMemory(String agentName, int limit) {
        return agentMemoryRepo.findRecentMemory(agentName, Math.min(limit, 1000));
    }
    
    /**
     * Get memory by trace hash
     */
    public Optional<AgentMemory> getMemoryByHash(String hash) {
        return agentMemoryRepo.findByTraceHash(hash);
    }
    
    /**
     * Get all memory for an agent
     */
    public List<AgentMemory> getAllMemoryForAgent(String agentName) {
        return agentMemoryRepo.findByAgentNameOrderByTimestampDesc(agentName);
    }
    
    /**
     * Count memory entries for an agent
     */
    public long countAgentMemory(String agentName) {
        return agentMemoryRepo.countByAgentName(agentName);
    }
    
    // ============ Trace Log Methods ============
    
    /**
     * Save trace without compression (for stability)
     */
    public TraceLog saveTrace(String agent, String input, String output, Integer durationMs) throws Exception {
        TraceLog trace = new TraceLog(agent, input, output, durationMs);
        
        // Note: Compression disabled temporarily to fix bytea serialization issues
        // The trace will be saved uncompressed for now
        
        TraceLog saved = traceLogRepo.save(trace);
        org.slf4j.LoggerFactory.getLogger(StorageService.class).info("Saved trace for agent '{}' (ID: {}, duration: {}ms)", agent, saved.getId(), durationMs);
        return saved;
    }
    
    /**
     * Load recent traces for an agent
     */
    public List<TraceLog> loadTraces(String agent, int limit) {
        return traceLogRepo.findRecentTraces(agent, Math.min(limit, 1000));
    }
    
    /**
     * Get traces within a time range
     */
    public List<TraceLog> getTracesInRange(String agent, LocalDateTime startTime, LocalDateTime endTime) {
        return traceLogRepo.findByAgentAndTimestampBetween(agent, startTime, endTime);
    }
    
    /**
     * Get all traces for an agent
     */
    public List<TraceLog> getAllTraces(String agent) {
        return traceLogRepo.findByAgentOrderByTimestampDesc(agent);
    }
    
    /**
     * Count traces for an agent
     */
    public long countAgentTraces(String agent) {
        return traceLogRepo.countByAgent(agent);
    }
    
    // ============ Cleanup & Maintenance Methods ============
    
    /**
     * Archive old memory entries (delete from main table)
     */
    @Transactional
    public void archiveOldMemory(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        agentMemoryRepo.deleteOldMemory(cutoff);
        org.slf4j.LoggerFactory.getLogger(StorageService.class).info("Archived agent memory older than {} days (cutoff: {})", daysOld, cutoff);
    }
    
    /**
     * Archive old trace logs
     */
    @Transactional
    public void archiveOldTraces(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        traceLogRepo.deleteOldTraces(cutoff);
        org.slf4j.LoggerFactory.getLogger(StorageService.class).info("Archived trace logs older than {} days (cutoff: {})", daysOld, cutoff);
    }
    
    // ============ Utility Methods ============
    
    /**
     * Compute SHA256 hash for deduplication
     */
    private String computeSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA256 computation failed", e);
        }
    }
    
    /**
     * Get storage statistics
     */
    public StorageStats getStorageStats() {
        return new StorageStats(
            agentMemoryRepo.count(),
            traceLogRepo.count()
        );
    }
    
    /**
     * Storage statistics class
     */
    public static class StorageStats {
        public long totalMemoryEntries;
        public long totalTraceEntries;
        
        public StorageStats(long memoryCount, long traceCount) {
            this.totalMemoryEntries = memoryCount;
            this.totalTraceEntries = traceCount;
        }
        
        @Override
        public String toString() {
            return "StorageStats{" +
                "memories=" + totalMemoryEntries +
                ", traces=" + totalTraceEntries +
                '}';
        }
    }
}
