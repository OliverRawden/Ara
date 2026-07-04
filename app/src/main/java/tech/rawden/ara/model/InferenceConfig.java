package tech.rawden.ara.model;

/** Mutable runtime inference settings and privacy tool toggles (synced from {@link AppSettings}). */
public class InferenceConfig {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are Ara, a witty and formal AI assistant created by Rawden. You are helpful, intelligent, and "
                    + "occasionally humorous with a dry wit. You maintain professionalism but are not afraid to show "
                    + "personality. You are concise when needed and elaborate when appropriate.\n\n"
                    + "A compact Vex Protocol Catalog is appended below. Agent tools use ara-tool names in "
                    + "<|tool_call|> (not protocol IDs). For ~/Documents/Ara/context.md use read_memory, "
                    + "write_memory, or append_memory — never shell cat/echo/tee. For structured facts use "
                    + "memory graph tools (query_memory_graph, upsert_memory_entity, link_memory_entities). "
                    + "Teams: /team <protocol-id> activates multi-agent handoff.";

    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final int DEFAULT_MAX_CONTEXT_CHARS = 28_000;

    private volatile float temperature = 0.7f;
    private volatile int maxTokens = DEFAULT_MAX_TOKENS;
    private volatile int maxContextChars = DEFAULT_MAX_CONTEXT_CHARS;
    private volatile int topK = 40;
    private volatile boolean webSearchEnabled = true;
    private volatile boolean terminalEnabled = true;
    private volatile boolean contextMemoryEnabled = true;
    private volatile boolean requireTerminalConfirmation = true;
    private volatile String systemPrompt = DEFAULT_SYSTEM_PROMPT;

    public static InferenceConfig defaults() {
        return new InferenceConfig();
    }

    public float temperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int maxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public int topK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean webSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    public boolean terminalEnabled() {
        return terminalEnabled;
    }

    public void setTerminalEnabled(boolean terminalEnabled) {
        this.terminalEnabled = terminalEnabled;
    }

    public boolean contextMemoryEnabled() {
        return contextMemoryEnabled;
    }

    public void setContextMemoryEnabled(boolean contextMemoryEnabled) {
        this.contextMemoryEnabled = contextMemoryEnabled;
    }

    public boolean requireTerminalConfirmation() {
        return requireTerminalConfirmation;
    }

    public void setRequireTerminalConfirmation(boolean requireTerminalConfirmation) {
        this.requireTerminalConfirmation = requireTerminalConfirmation;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
