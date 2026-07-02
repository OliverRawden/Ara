package tech.rawden.ara.tool;

public record ToolDefinition(
        String name, String description, String parametersJson, String toolGroup, boolean builtin) {

    public boolean isTerminalTool() {
        return "terminal".equals(toolGroup);
    }

    public boolean isWebTool() {
        return "web".equals(toolGroup);
    }

    public boolean isMemoryTool() {
        return "memory".equals(toolGroup);
    }
}
