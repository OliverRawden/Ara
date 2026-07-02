package tech.rawden.ara.model;

public class AppSettings {

    private float temperature = 0.7f;
    private int maxTokens = InferenceConfig.DEFAULT_MAX_TOKENS;
    private String systemPrompt = InferenceConfig.DEFAULT_SYSTEM_PROMPT;
    private boolean darkMode = true;
    private boolean webSearchEnabled = true;
    private boolean terminalEnabled = true;
    private boolean requireTerminalConfirmation = true;
    private boolean contextMemoryEnabled = true;
    private boolean encryptionEnabled = false;
    private boolean useSystemAccent = true;
    private String selectedModel = "";

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
    }

    public boolean isTerminalEnabled() {
        return terminalEnabled;
    }

    public void setTerminalEnabled(boolean terminalEnabled) {
        this.terminalEnabled = terminalEnabled;
    }

    public boolean isRequireTerminalConfirmation() {
        return requireTerminalConfirmation;
    }

    public void setRequireTerminalConfirmation(boolean requireTerminalConfirmation) {
        this.requireTerminalConfirmation = requireTerminalConfirmation;
    }

    public boolean isContextMemoryEnabled() {
        return contextMemoryEnabled;
    }

    public void setContextMemoryEnabled(boolean contextMemoryEnabled) {
        this.contextMemoryEnabled = contextMemoryEnabled;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
    }

    public String getSelectedModel() {
        return selectedModel;
    }

    public void setSelectedModel(String selectedModel) {
        this.selectedModel = selectedModel;
    }

    public boolean isUseSystemAccent() {
        return useSystemAccent;
    }

    public void setUseSystemAccent(boolean useSystemAccent) {
        this.useSystemAccent = useSystemAccent;
    }
}
