package tech.rawden.ara.ai;

import de.kherud.llama.args.CacheType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * llama.cpp load parameters per routing tier. Heavy profiles are resolved at load time from
 * unified RAM and GGUF size (Ollama-style: full Metal offload, mmap, ctx sized to headroom).
 */
public record ModelLoadProfile(
        ModelTier tier,
        int ctxSize,
        int batchSize,
        int ubatchSize,
        int gpuLayers,
        CacheType cacheK,
        CacheType cacheV,
        int maxContextChars,
        int maxGenerateTokens,
        boolean fullSystemWarmup,
        boolean compactPrompt) {

    private static final Logger LOG = Logger.getLogger(ModelLoadProfile.class.getName());

    private static final long OS_HEADROOM_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int SYSTEM_PROMPT_TOKEN_RESERVE = 2_500;
    private static final int OUTPUT_TOKEN_RESERVE = 2_048;
    private static final double CHARS_PER_TOKEN_ESTIMATE = 3.5;

    public static final ModelLoadProfile LIGHT = new ModelLoadProfile(
            ModelTier.LIGHT,
            16_384,
            4096,
            2048,
            99,
            CacheType.Q8_0,
            CacheType.Q4_0,
            28_000,
            32_768,
            true,
            false);

    public static ModelLoadProfile forTier(ModelTier tier) {
        return tier == ModelTier.HEAVY ? resolveHeavy(null) : LIGHT;
    }

    public static ModelLoadProfile forTier(ModelTier tier, Path modelPath) {
        return tier == ModelTier.HEAVY ? resolveHeavy(modelPath) : LIGHT;
    }

    public static ModelLoadProfile forPath(Path modelPath) {
        if (modelPath == null) {
            return LIGHT;
        }
        String name = modelPath.getFileName().toString().toLowerCase();
        if (name.contains("32b") || name.contains("30b") || name.contains("70b") || name.contains("34b")) {
            return resolveHeavy(modelPath);
        }
        try {
            if (Files.size(modelPath) > 12L * 1024 * 1024 * 1024) {
                return resolveHeavy(modelPath);
            }
        } catch (Exception ignored) {
        }
        return LIGHT;
    }

    /**
     * Picks ctx/batch from available unified memory after model weights. Uses full GPU layer offload
     * (99) like Ollama on Apple Silicon — partial offload can increase peak memory on 24 GB Macs.
     */
    public static ModelLoadProfile resolveHeavy(Path modelPath) {
        long totalRam = SystemMemory.totalBytes();
        long modelBytes = 0;
        if (modelPath != null) {
            try {
                modelBytes = Files.size(modelPath);
            } catch (Exception ignored) {
            }
        }
        if (modelBytes <= 0) {
            modelBytes = 19L * 1024 * 1024 * 1024;
        }

        long headroom = totalRam - modelBytes - OS_HEADROOM_BYTES;

        int ctx;
        int batch;
        int ubatch;

        if (totalRam >= 22L * 1024 * 1024 * 1024 && headroom >= 3L * 1024 * 1024 * 1024) {
            ctx = 8192;
            batch = 2048;
            ubatch = 512;
        } else if (totalRam >= 18L * 1024 * 1024 * 1024 && headroom >= 2L * 1024 * 1024 * 1024) {
            ctx = 6144;
            batch = 1024;
            ubatch = 256;
        } else {
            ctx = 4096;
            batch = 512;
            ubatch = 128;
        }

        int historyTokenBudget =
                Math.max(512, ctx - SYSTEM_PROMPT_TOKEN_RESERVE - OUTPUT_TOKEN_RESERVE);
        int maxContextChars = (int) (historyTokenBudget * CHARS_PER_TOKEN_ESTIMATE);

        var profile = new ModelLoadProfile(
                ModelTier.HEAVY,
                ctx,
                batch,
                ubatch,
                99,
                CacheType.Q4_0,
                CacheType.Q4_0,
                maxContextChars,
                4096,
                true,
                true);

        LOG.info(String.format(
                "Heavy profile: totalRam=%.1fGB model=%.1fGB headroom=%.1fGB ctx=%d batch=%d/%d gpu=%d maxHistoryChars=%d",
                totalRam / 1e9,
                modelBytes / 1e9,
                headroom / 1e9,
                ctx,
                batch,
                ubatch,
                profile.gpuLayers(),
                maxContextChars));

        return profile;
    }
}