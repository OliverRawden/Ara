package tech.rawden.ara.integration;

import tech.rawden.ara.core.AraPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class VexProtocolLoader {

    private static final Logger LOG = Logger.getLogger(VexProtocolLoader.class.getName());
    private static final String SCHEMA_MARKER = "```json schema";

    private VexProtocolLoader() {}

    public static List<VexProtocol> loadAll() {
        var dir = AraPaths.vexProtocolsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        var protocols = new ArrayList<VexProtocol>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                    .sorted(Comparator.comparingInt(VexProtocolLoader::protocolIdOrMax))
                    .forEach(p -> parse(p).ifPresent(protocols::add));
        } catch (IOException e) {
            LOG.warning("Failed to load Vex protocols: " + e.getMessage());
        }
        return protocols;
    }

    private static int protocolIdOrMax(Path path) {
        return parse(path).map(VexProtocol::id).orElse(Integer.MAX_VALUE);
    }

    static java.util.Optional<VexProtocol> parse(Path path) {
        try {
            var content = Files.readString(path);
            var doc = parseFrontmatter(path, content);
            var idRaw = doc.get("id");
            if (idRaw == null || idRaw.isBlank()) {
                return java.util.Optional.empty();
            }
            var description = stripSchemaBlock(doc.body()).strip();
            if (description.isBlank()) {
                description = doc.get("name");
            }
            var parameters = doc.get("parameters");
            if (parameters == null || parameters.isBlank()) {
                parameters = extractSchemaFromBody(doc.body());
            }
            var name = doc.get("name");
            if (name == null || name.isBlank()) {
                var fileName = path.getFileName().toString();
                var dot = fileName.lastIndexOf('.');
                name = dot > 0 ? fileName.substring(0, dot) : fileName;
            }
            return java.util.Optional.of(new VexProtocol(
                    Integer.parseInt(idRaw.strip()),
                    name,
                    doc.get("type") != null ? doc.get("type") : "action",
                    doc.get("action"),
                    doc.get("modifier"),
                    doc.get("target"),
                    doc.get("args"),
                    firstNonBlank(doc.get("max-severity"), doc.get("max-arg")),
                    doc.get("ara-tool"),
                    doc.get("tool-group"),
                    doc.get("source"),
                    "true".equalsIgnoreCase(doc.get("builtin")),
                    description,
                    parameters));
        } catch (Exception e) {
            LOG.warning("Failed to parse Vex protocol " + path + ": " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static ParsedDoc parseFrontmatter(Path path, String content) {
        var trimmed = content.stripLeading();
        if (!trimmed.startsWith("---")) {
            return new ParsedDoc(path, Map.of(), content);
        }
        var lines = trimmed.split("\n", -1);
        var frontmatter = new LinkedHashMap<String, String>();
        var end = -1;
        for (int i = 1; i < lines.length; i++) {
            var line = lines[i].trim();
            if (line.equals("---")) {
                end = i;
                break;
            }
            var colon = line.indexOf(':');
            if (colon > 0) {
                frontmatter.put(
                        line.substring(0, colon).trim(),
                        line.substring(colon + 1).trim());
            }
        }
        var body = end >= 0 && end + 1 < lines.length
                ? String.join("\n", java.util.Arrays.copyOfRange(lines, end + 1, lines.length))
                : "";
        return new ParsedDoc(path, frontmatter, body);
    }

    private static String extractSchemaFromBody(String body) {
        if (body == null) {
            return null;
        }
        var markerIndex = body.indexOf(SCHEMA_MARKER);
        if (markerIndex < 0) {
            return null;
        }
        var start = body.indexOf('\n', markerIndex);
        if (start < 0) {
            return null;
        }
        var end = body.indexOf("```", start + 1);
        if (end < 0) {
            return null;
        }
        var json = body.substring(start + 1, end).strip();
        return json.isBlank() ? null : json;
    }

    private static String stripSchemaBlock(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        var markerIndex = body.indexOf(SCHEMA_MARKER);
        if (markerIndex < 0) {
            return body;
        }
        var end = body.indexOf("```", markerIndex + SCHEMA_MARKER.length());
        if (end < 0) {
            return body.substring(0, markerIndex).strip();
        }
        var after = body.indexOf("```", end + 3);
        var tail = after >= 0 ? body.substring(after + 3) : "";
        return (body.substring(0, markerIndex) + tail).strip();
    }

    private record ParsedDoc(Path path, Map<String, String> frontmatter, String body) {
        String get(String key) {
            return frontmatter.get(key);
        }
    }
}
