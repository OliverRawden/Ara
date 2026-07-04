package tech.rawden.ara.model;

import tech.rawden.ara.ai.RoutingMode;

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
    /** Light model filename (hot). Defaults to {@link #selectedModel} when blank. */
    private String lightModel = "";
    /** Heavy model filename (on-demand). */
    private String heavyModel = "";
    /** AUTO, LIGHT_ONLY, or HEAVY_ONLY. */
    private String routingMode = RoutingMode.AUTO.name();
    /** Download chunk size in megabytes (8–16 recommended). */
    private int downloadChunkSizeMb = 12;
    private int maxConcurrentConnections = 4;
    /** When true, heavy model download uses Ara repo manifest. */
    private boolean downloadHeavyFromRepo = true;

    /** When true, a background check runs once after startup (never blocks launch). Default off for privacy. */
    private boolean checkForUpdatesOnStartup = false;

    /** When true, shows the live developer log window and captures verbose diagnostics. */
    private boolean developerMode = false;

    /** ISO-8601 timestamp of the last manual or automatic update check (null if never). */
    private String lastUpdateCheckAt;

    /** Short status from the last check, e.g. "up to date", "update available", "failed". */
    private String lastUpdateCheckStatus = "never";

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

    public String getLightModel() {
        return lightModel != null && !lightModel.isBlank() ? lightModel : selectedModel;
    }

    public void setLightModel(String lightModel) {
        this.lightModel = lightModel;
    }

    public String getHeavyModel() {
        return heavyModel;
    }

    public void setHeavyModel(String heavyModel) {
        this.heavyModel = heavyModel;
    }

    public RoutingMode getRoutingMode() {
        return RoutingMode.fromString(routingMode);
    }

    public void setRoutingMode(RoutingMode mode) {
        this.routingMode = mode != null ? mode.name() : RoutingMode.AUTO.name();
    }

    public int getDownloadChunkSizeMb() {
        return downloadChunkSizeMb > 0 ? downloadChunkSizeMb : 12;
    }

    public void setDownloadChunkSizeMb(int downloadChunkSizeMb) {
        this.downloadChunkSizeMb = downloadChunkSizeMb;
    }

    public int getMaxConcurrentConnections() {
        return maxConcurrentConnections > 0 ? maxConcurrentConnections : 4;
    }

    public void setMaxConcurrentConnections(int maxConcurrentConnections) {
        this.maxConcurrentConnections = maxConcurrentConnections;
    }

    public boolean isDownloadHeavyFromRepo() {
        return downloadHeavyFromRepo;
    }

    public void setDownloadHeavyFromRepo(boolean downloadHeavyFromRepo) {
        this.downloadHeavyFromRepo = downloadHeavyFromRepo;
    }

    public boolean isUseSystemAccent() {
        return useSystemAccent;
    }

    public void setUseSystemAccent(boolean useSystemAccent) {
        this.useSystemAccent = useSystemAccent;
    }

    public boolean isCheckForUpdatesOnStartup() {
        return checkForUpdatesOnStartup;
    }

    public void setCheckForUpdatesOnStartup(boolean checkForUpdatesOnStartup) {
        this.checkForUpdatesOnStartup = checkForUpdatesOnStartup;
    }

    public String getLastUpdateCheckAt() {
        return lastUpdateCheckAt;
    }

    public void setLastUpdateCheckAt(String lastUpdateCheckAt) {
        this.lastUpdateCheckAt = lastUpdateCheckAt;
    }

    public String getLastUpdateCheckStatus() {
        return lastUpdateCheckStatus;
    }

    public void setLastUpdateCheckStatus(String lastUpdateCheckStatus) {
        this.lastUpdateCheckStatus = lastUpdateCheckStatus;
    }

    public boolean isDeveloperMode() {
        return developerMode;
    }

    public void setDeveloperMode(boolean developerMode) {
        this.developerMode = developerMode;
    }
}
