package tech.rawden.ara.ai;

import de.kherud.llama.args.CacheType;

import java.nio.file.Files;
import java.nio.file.Path;

/** llama.cpp load parameters tuned per tier — heavy 32B needs a much smaller KV budget on 24 GB Macs. */
public enum ModelLoadProfile {
    LIGHT(16_384, 4096, 2048, 99, CacheType.Q8_0, CacheType.Q4_0, 28_000, 32_768, true),
    /** ~19 GB model + OS headroom: small ctx/batch avoids GPU OOM and KV overflow on M-series 24 GB. */
    HEAVY(6144, 512, 128, 70, CacheType.Q4_0, CacheType.Q4_0, 8_000, 4096, false);

    private final int ctxSize;
    private final int batchSize;
    private final int ubatchSize;
    private final int gpuLayers;
    private final CacheType cacheK;
    private final CacheType cacheV;
    private final int maxContextChars;
    private final int maxGenerateTokens;
    private final boolean fullSystemWarmup;

    ModelLoadProfile(
            int ctxSize,
            int batchSize,
            int ubatchSize,
            int gpuLayers,
            CacheType cacheK,
            CacheType cacheV,
            int maxContextChars,
            int maxGenerateTokens,
            boolean fullSystemWarmup) {
        this.ctxSize = ctxSize;
        this.batchSize = batchSize;
        this.ubatchSize = ubatchSize;
        this.gpuLayers = gpuLayers;
        this.cacheK = cacheK;
        this.cacheV = cacheV;
        this.maxContextChars = maxContextChars;
        this.maxGenerateTokens = maxGenerateTokens;
        this.fullSystemWarmup = fullSystemWarmup;
    }

    public int ctxSize() {
        return ctxSize;
    }

    public int batchSize() {
        return batchSize;
    }

    public int ubatchSize() {
        return ubatchSize;
    }

    public int gpuLayers() {
        return gpuLayers;
    }

    public CacheType cacheK() {
        return cacheK;
    }

    public CacheType cacheV() {
        return cacheV;
    }

    public int maxContextChars() {
        return maxContextChars;
    }

    public int maxGenerateTokens() {
        return maxGenerateTokens;
    }

    public boolean fullSystemWarmup() {
        return fullSystemWarmup;
    }

    public static ModelLoadProfile forTier(ModelTier tier) {
        return tier == ModelTier.HEAVY ? HEAVY : LIGHT;
    }

    public static ModelLoadProfile forPath(Path modelPath) {
        if (modelPath == null) {
            return LIGHT;
        }
        String name = modelPath.getFileName().toString().toLowerCase();
        if (name.contains("32b") || name.contains("30b") || name.contains("70b") || name.contains("34b")) {
            return HEAVY;
        }
        try {
            if (Files.size(modelPath) > 12L * 1024 * 1024 * 1024) {
                return HEAVY;
            }
        } catch (Exception ignored) {
        }
        return LIGHT;
    }
}