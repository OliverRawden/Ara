package tech.rawden.ara.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/** Fetches hosted GGUF metadata from the public Ara repository. */
public final class ModelCatalog {

    private static final Logger LOG = Logger.getLogger(ModelCatalog.class.getName());

    /** Same branch policy as {@link tech.rawden.ara.update.UpdateService} — metadata on {@code main}. */
    public static final String DEFAULT_METADATA_URL =
            "https://raw.githubusercontent.com/OliverRawden/Ara/main/installers/models.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String USER_AGENT = "Ara/" + tech.rawden.ara.update.AppVersion.current() + " (model-catalog)";

    private static volatile ModelRelease cached;

    private ModelCatalog() {}

    public static ModelRelease.DefaultModel resolveDefaultModel(HttpClient httpClient) {
        var release = fetchMetadata(httpClient);
        if (release != null && release.defaultModel != null) {
            return release.defaultModel;
        }
        return embeddedLightFallback();
    }

    public static ModelRelease.DefaultModel resolveHeavyModel(HttpClient httpClient) {
        var release = fetchMetadata(httpClient);
        if (release != null && release.heavyModel != null) {
            return release.heavyModel;
        }
        return embeddedHeavyFallback();
    }

    public static void invalidateCache() {
        cached = null;
    }

    private static ModelRelease fetchMetadata(HttpClient httpClient) {
        if (cached != null) {
            return cached;
        }
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_METADATA_URL))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warning("Model metadata HTTP " + response.statusCode() + " from " + DEFAULT_METADATA_URL);
                return null;
            }
            cached = MAPPER.readValue(response.body(), ModelRelease.class);
            LOG.info("Loaded model metadata: " + cached.defaultModel.filename);
            return cached;
        } catch (Exception e) {
            LOG.warning("Could not fetch model metadata: " + e.getMessage());
            return null;
        }
    }

    /** Used when metadata is unreachable (offline / not yet published on main). */
    private static ModelRelease.DefaultModel embeddedLightFallback() {
        LOG.info("Using embedded model metadata fallback");
        var model = new ModelRelease.DefaultModel();
        model.id = "qwen2.5-7b-instruct-q4_k_m";
        model.filename = "Qwen2.5-7B-Instruct-Q4_K_M.gguf";
        model.displayName = "Qwen2.5-7B-Instruct Q4_K_M";
        model.sizeBytes = 4_683_074_240L;
        model.sha256 = "65b8fcd92af6b4fefa935c625d1ac27ea29dcb6ee14589c55a8f115ceaaa1423";
        model.downloadUrl = null;
        var part0 = new ModelRelease.Part();
        part0.filename = model.filename + ".part0";
        part0.sizeBytes = 2_097_152_000L;
        part0.url =
                "https://github.com/OliverRawden/Ara/releases/download/models-v1/Qwen2.5-7B-Instruct-Q4_K_M.gguf.part0";
        var part1 = new ModelRelease.Part();
        part1.filename = model.filename + ".part1";
        part1.sizeBytes = 2_097_152_000L;
        part1.url =
                "https://github.com/OliverRawden/Ara/releases/download/models-v1/Qwen2.5-7B-Instruct-Q4_K_M.gguf.part1";
        var part2 = new ModelRelease.Part();
        part2.filename = model.filename + ".part2";
        part2.sizeBytes = 488_770_240L;
        part2.url =
                "https://github.com/OliverRawden/Ara/releases/download/models-v1/Qwen2.5-7B-Instruct-Q4_K_M.gguf.part2";
        model.parts = java.util.List.of(part0, part1, part2);
        return model;
    }

    private static ModelRelease.DefaultModel embeddedHeavyFallback() {
        var model = new ModelRelease.DefaultModel();
        model.id = "qwen2.5-coder-32b-q4_k_m";
        model.filename = "Qwen2.5-Coder-32B-Instruct-Q4_K_M.gguf";
        model.displayName = "Qwen2.5-Coder-32B Q4_K_M (Heavy)";
        model.sizeBytes = 19_851_336_672L;
        model.sha256 = "8e2fd78ff55e7cdf577fda257bac2776feb7d73d922613caf35468073807e815";
        model.downloadUrl = null;
        model.parts = embeddedHeavyParts(model.filename);
        return model;
    }

    private static java.util.List<ModelRelease.Part> embeddedHeavyParts(String base) {
        var tag = "models-heavy-v1";
        long chunk = 2_097_152_000L;
        long size = 19_851_336_672L;
        var parts = new java.util.ArrayList<ModelRelease.Part>();
        long offset = 0;
        for (int i = 0; offset < size; i++) {
            long partSize = Math.min(chunk, size - offset);
            var part = new ModelRelease.Part();
            part.filename = base + ".part" + i;
            part.sizeBytes = partSize;
            part.url = "https://github.com/OliverRawden/Ara/releases/download/" + tag + "/" + part.filename;
            parts.add(part);
            offset += partSize;
        }
        return parts;
    }
}
