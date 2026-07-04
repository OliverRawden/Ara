package tech.rawden.ara.integration;

import tech.rawden.ara.tool.ToolDefinition;

public record VexProtocol(
        int id,
        String name,
        String type,
        String action,
        String modifier,
        String target,
        String args,
        String maxArg,
        String araTool,
        String toolGroup,
        String source,
        boolean builtin,
        String description,
        String parametersJson,
        String membersJson,
        String handoffMode) {

    public boolean isAraTool() {
        return araTool != null && !araTool.isBlank();
    }

    public boolean isTeam() {
        return "team".equalsIgnoreCase(type);
    }

    public boolean isModifier() {
        return "modifier".equalsIgnoreCase(type);
    }

    public ToolDefinition toToolDefinition() {
        var params = parametersJson != null && !parametersJson.isBlank()
                ? parametersJson
                : "{\"type\":\"object\",\"properties\":{}}";
        return new ToolDefinition(araTool, description, params, toolGroup, builtin);
    }
}
