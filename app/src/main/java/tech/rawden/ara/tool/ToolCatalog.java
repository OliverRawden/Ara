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
        return getCompactToolList(includeWebSearch, includeSecureMemory, includeTerminal);
    }

    /** Token-efficient tool list for inference (ChatML tool_call JSON, not OpenAI function schema). */
    public static String getCompactToolList(
            boolean includeWebSearch, boolean includeSecureMemory, boolean includeTerminal) {
        var sb = new StringBuilder();
        for (var tool : tools()) {
            if (!isEnabled(tool, includeWebSearch, includeSecureMemory, includeTerminal)) {
                continue;
            }
            sb.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(inferenceHint(tool.name()))
                    .append('\n');
        }
        return sb.toString().strip();
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

    /** Short, action-oriented hints — avoids protocol wording that triggers spurious tool calls. */
    private static String inferenceHint(String name) {
        return switch (name) {
            case "get_current_datetime" -> "date/time — only when the user explicitly asks";
            case "read_memory" -> "read context.md — when a task needs prior context, not on greetings";
            case "write_memory" -> "replace context.md";
            case "append_memory" -> "append to context.md";
            case "query_memory_graph" -> "query SQLite entity/relation memory graph";
            case "upsert_memory_entity" -> "create or update a memory graph entity";
            case "link_memory_entities" -> "link two memory graph entities";
            case "web_search" -> "search the web for live facts";
            case "execute_command" -> "run a shell command";
            default -> "see Vex catalog";
        };
    }
}
