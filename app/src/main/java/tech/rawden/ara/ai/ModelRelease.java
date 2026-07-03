package tech.rawden.ara.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Default GGUF metadata from {@code installers/models.json} on the Ara GitHub repo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelRelease {

    public int schemaVersion;
    public DefaultModel defaultModel;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DefaultModel {
        public String id;
        public String filename;
        public String displayName;
        public long sizeBytes;
        public String sha256;
        /** Single-file download (e.g. Git LFS media URL). Mutually exclusive with {@link #parts}. */
        public String downloadUrl;
        /** Multi-part release assets (each part must be &lt; 2 GiB for GitHub Releases). */
        public List<Part> parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        public String filename;
        public String url;
        public long sizeBytes;
    }
}
