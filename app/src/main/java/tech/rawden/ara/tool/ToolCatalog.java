package tech.rawden.ara.tool;

import tech.rawden.ara.integration.VexProtocolCatalog;

import java.util.List;
import java.util.logging.Logger;

/**
 * Facade over {@link tech.rawden.ara.integration.VexProtocolCatalog} for agent-tool JSON injected
 * into inference prompts. Reloaded at startup in {@link tech.rawden.ara.Main}.
 */
public final class ToolCatalog {

    private static final Logger LOG = Logger.getLogger(ToolCatalog.class.getName());

    private ToolCatalog() {}

    public static void reload() {
        VexProtocolCatalog.reload();
        var tools = VexProtocolCatalog.araTools();
        if (tools.isEmpty()) {
            LOG.warning("No ara-tool protocols found in ~/Documents/Vex/Protocols/. "
                    + "Launch Vex once to seed built-in tools (101–106).");
        } else {
            LOG.info("Loaded " + tools.size() + " agent tools from Vex protocols");
        }
    }

    public static List<ToolDefinition> tools() {
        return VexProtocolCatalog.araTools();
    }

    public static String getFunctionDefinitions(
            boolean includeWebSearch, boolean includeSecureMemory, boolean includeTerminal) {
        var sb = new StringBuilder("[\n");
        var first = true;
        for (var tool : tools()) {
            if (!isEnabled(tool, includeWebSearch, includeSecureMemory, includeTerminal)) {
                continue;
            }
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append(toFunctionJson(tool));
        }
        sb.append("\n]");
        return sb.toString();
    }

    public static ToolDefinition findByName(String name) {
        return tools().stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    private static boolean isEnabled(
            ToolDefinition tool, boolean includeWebSearch, boolean includeSecureMemory, boolean includeTerminal) {
        if (tool.isTerminalTool()) {
            return includeTerminal;
        }
        if (tool.isWebTool()) {
            return includeWebSearch;
        }
        if (tool.isMemoryTool()) {
            return includeSecureMemory;
        }
        return true;
    }

    private static String toFunctionJson(ToolDefinition tool) {
        var protocol = VexProtocolCatalog.findByAraTool(tool.name());
        var description = tool.description();
        if (protocol.isPresent()) {
            description = description + " [Vex protocol " + protocol.get().id() + "]";
        }
        return """
                {
                    "type": "function",
                    "function": {
                        "name": "%s",
                        "description": %s,
                        "parameters": %s
                    }
                }""".formatted(escapeJson(tool.name()), jsonString(description), tool.parametersJson());
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
