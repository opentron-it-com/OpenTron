package org.opentron.backend.storage.service;

import com.github.luben.zstd.Zstd;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Base64;

/**
 * Compression service using Zstd for efficient storage
 * Handles compression/decompression of traces and memory
 */
@Service
public class CompressionService {
    
    private static final int COMPRESSION_LEVEL = 3;  // Balance speed vs compression ratio
    private static final long MAX_DECOMPRESSED_SIZE = 100 * 1024 * 1024;  // 100MB max
    private static final Logger logger = LoggerFactory.getLogger(CompressionService.class);
    
    /**
     * Compress raw bytes using Zstd
     */
    public byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        byte[] compressed = Zstd.compress(data, COMPRESSION_LEVEL);
        logger.info("Compressed {} bytes to {} bytes", data.length, compressed.length);
        return compressed;
    }
    
    /**
     * Decompress bytes compressed with Zstd
     */
    @SuppressWarnings("deprecation")
    public byte[] decompress(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        
        long size = Zstd.decompressedSize(compressedData);
        if (size <= 0 || size > MAX_DECOMPRESSED_SIZE) {
            size = 1024 * 1024;  // Default 1MB
        }
        
        byte[] decompressed = new byte[(int) size];
        long decompressedSize = Zstd.decompress(decompressed, compressedData);
        
        if (decompressedSize < 0) {
            throw new RuntimeException("Decompression failed");
        }
        
        if (decompressedSize < decompressed.length) {
            byte[] result = new byte[(int) decompressedSize];
            System.arraycopy(decompressed, 0, result, 0, (int) decompressedSize);
            return result;
        }
        
        return decompressed;
    }
    
    /**
     * Compress text and encode to Base64 string
     */
    public String compressAndEncode(String text) throws Exception {
        byte[] compressed = compress(text.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(compressed);
    }
    
    /**
     * Decode Base64 string and decompress to text
     */
    public String decodeAndDecompress(String encoded) throws Exception {
        byte[] compressed = Base64.getDecoder().decode(encoded);
        byte[] decompressed = decompress(compressed);
        return new String(decompressed, "UTF-8");
    }
    
    /**
     * Calculate compression ratio (useful for monitoring)
     */
    public double getCompressionRatio(byte[] original, byte[] compressed) {
        if (original.length == 0) return 0;
        return (double) compressed.length / original.length * 100;
    }
}
