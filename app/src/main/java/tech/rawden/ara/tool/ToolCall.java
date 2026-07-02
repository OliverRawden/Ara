package tech.rawden.ara.tool;

import com.fasterxml.jackson.databind.ObjectMapper;

public record ToolCall(String name, String arguments) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolCall parse(String json) {
        try {
            var tree = MAPPER.readTree(json);
            var name = tree.get("name").asText();
            var args = tree.get("arguments").toString();
            return new ToolCall(name, args);
        } catch (Exception e) {
            return null;
        }
    }

    public String getCommand() {
        if (!"execute_command".equals(name)) return null;
        try {
            var args = MAPPER.readTree(arguments);
            return args.has("command") ? args.get("command").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getQuery() {
        if (!"web_search".equals(name)) return null;
        try {
            var args = MAPPER.readTree(arguments);
            return args.has("query") ? args.get("query").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getFunctionDefinitions(
            boolean includeWebSearch, boolean includeSecureMemory, boolean includeTerminal) {
        return ToolCatalog.getFunctionDefinitions(includeWebSearch, includeSecureMemory, includeTerminal);
    }

    public static String getFunctionDefinitions(boolean includeWebSearch) {
        return getFunctionDefinitions(includeWebSearch, true, true);
    }

    public static String getFunctionDefinitions(boolean includeWebSearch, boolean includeSecureMemory) {
        return getFunctionDefinitions(includeWebSearch, includeSecureMemory, true);
    }

    public String getMemoryContent() {
        if (!"write_memory".equals(name) && !"append_memory".equals(name)) return null;
        try {
            var args = MAPPER.readTree(arguments);
            if ("write_memory".equals(name)) {
                return args.has("content") ? args.get("content").asText() : null;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isKnownTool() {
        return ToolCatalog.findByName(name) != null;
    }
}
