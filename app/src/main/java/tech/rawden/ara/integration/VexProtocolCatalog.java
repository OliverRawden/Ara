package tech.rawden.ara.integration;

import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.tool.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Cached view of all Vex protocol markdown files. Auto-refreshes when
 * {@link tech.rawden.ara.core.AraPaths#vexProtocolsDir()} mtime changes; formatted section is
 * appended to every system prompt.
 */
public final class VexProtocolCatalog {

    private static final Logger LOG = Logger.getLogger(VexProtocolCatalog.class.getName());

    private static volatile List<VexProtocol> cached = List.of();
    private static volatile long lastDirSnapshot;

    private VexProtocolCatalog() {}

    public static void reload() {
        cached = List.copyOf(VexProtocolLoader.loadAll());
        lastDirSnapshot = directorySnapshot();
        LOG.info("Loaded " + cached.size() + " Vex protocols from " + AraPaths.vexProtocolsDir());
    }

    public static List<VexProtocol> protocols() {
        ensureFresh();
        return cached;
    }

    public static List<ToolDefinition> araTools() {
        return protocols().stream()
                .filter(VexProtocol::isAraTool)
                .map(VexProtocol::toToolDefinition)
                .toList();
    }

    public static Optional<VexProtocol> findById(int id) {
        return protocols().stream().filter(p -> p.id() == id).findFirst();
    }

    public static Optional<VexProtocol> findByAraTool(String araTool) {
        return protocols().stream().filter(p -> araTool.equals(p.araTool())).findFirst();
    }

    public static String formatCatalogSection() {
        return formatCompactCatalogSection();
    }

    /**
     * Token-efficient catalog for inference prompts. Full protocol detail lives in Vex; the model only
     * needs IDs, ara-tool names, and one-line hints — not multi-hundred-character descriptions every turn.
     */
    public static String formatCompactCatalogSection() {
        var list = protocols();
        if (list.isEmpty()) {
            return "\n\n## Vex Protocol Catalog\n(empty — launch Vex once to seed ~/Documents/Vex/Protocols/)\n";
        }

        var sb = new StringBuilder();
        sb.append("\n\n## Vex Protocol Catalog\n");
        sb.append("Source: ~/Documents/Vex/Protocols/. Console: run by ID (`1`, `2|9`, `10` kill). ");
        sb.append("Agent: <|tool_call|> with ara-tool name (not protocol ID).\n");

        var console = list.stream().filter(p -> !p.isAraTool()).toList();
        if (!console.isEmpty()) {
            sb.append("Console: ");
            for (int i = 0; i < console.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                var p = console.get(i);
                sb.append(p.id()).append('=').append(p.name());
            }
            sb.append('\n');
        }

        var ara = list.stream().filter(VexProtocol::isAraTool).toList();
        if (!ara.isEmpty()) {
            sb.append("Ara-tools: ");
            for (int i = 0; i < ara.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                var p = ara.get(i);
                sb.append(p.id()).append('=').append(p.araTool());
            }
            sb.append('\n');
        }

        sb.append("Pipe: left|right (right modifier first; 10 aborts left).\n");
        return sb.toString();
    }

    private static String formatLine(VexProtocol p) {
        var meta = new StringBuilder();
        meta.append("- **").append(p.id()).append("** ").append(p.name()).append(" (");
        meta.append(p.type());
        if (p.isAraTool()) {
            meta.append(", ara-tool `").append(p.araTool()).append("`");
        }
        if (p.action() != null && !p.action().isBlank()) {
            meta.append(", action=").append(p.action());
        }
        if (p.modifier() != null && !p.modifier().isBlank()) {
            meta.append(", modifier=").append(p.modifier());
        }
        if (p.target() != null && !p.target().isBlank()) {
            meta.append(", target=").append(p.target());
        }
        if (p.args() != null && !p.args().isBlank()) {
            meta.append(", args={").append(p.args()).append("}");
            if (p.maxArg() != null && !p.maxArg().isBlank()) {
                meta.append(" 1–").append(p.maxArg());
            }
        }
        meta.append(") — ");
        meta.append(truncate(p.description(), 320));
        return meta.toString();
    }

    private static String truncate(String text, int max) {
        if (text == null || text.isBlank()) {
            return "(no description)";
        }
        var oneLine = text.replace('\n', ' ').strip();
        if (oneLine.length() <= max) {
            return oneLine;
        }
        return oneLine.substring(0, max - 3) + "...";
    }

    private static void ensureFresh() {
        var snapshot = directorySnapshot();
        if (cached.isEmpty() || snapshot != lastDirSnapshot) {
            reload();
        }
    }

    private static long directorySnapshot() {
        var dir = AraPaths.vexProtocolsDir();
        try {
            if (!Files.isDirectory(dir)) {
                return 0;
            }
            long max = Files.getLastModifiedTime(dir).toMillis();
            try (var stream = Files.list(dir)) {
                for (var path : stream.toList()) {
                    if (path.toString().endsWith(".md")) {
                        max = Math.max(max, Files.getLastModifiedTime(path).toMillis());
                    }
                }
            }
            return max;
        } catch (IOException e) {
            return 0;
        }
    }
}
