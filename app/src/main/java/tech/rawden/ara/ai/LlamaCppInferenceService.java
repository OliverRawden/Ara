package tech.rawden.ara.ai;

import tech.rawden.ara.integration.VexProtocolCatalog;
import tech.rawden.ara.model.ChatMessage;
import tech.rawden.ara.model.InferenceConfig;
import tech.rawden.ara.tool.ToolCall;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.CacheType;
import de.kherud.llama.args.NumaStrategy;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * On-device inference via java-llama.cpp. Builds ChatML prompts with Vex catalog + tool JSON,
 * streams tokens, and detects {@code <|tool_call|>} markers for the agent loop in
 * {@link tech.rawden.ara.ui.ChatViewComp}.
 */
public class LlamaCppInferenceService implements InferenceService {

    private static final Logger LOG = Logger.getLogger(LlamaCppInferenceService.class.getName());

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm");

    private static final String TOOL_POLICY =
            "Tool policy: reply directly to greetings, thanks, and small talk — call no tools. "
                    + "Use tools only when the user clearly needs an action or data you lack. "
                    + "Never call get_current_datetime unless they ask about date or time. "
                    + "Never call read_memory on a simple hello.";

    private static final String TOOL_POLICY_COMPACT =
            "Tools: only when needed for actions/data you lack; no tools for greetings or small talk.";

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Object loadLock = new Object();

    private volatile Status status = Status.UNLOADED;
    private volatile String modelName = "None";
    private volatile LlamaModel model;

    private volatile ModelLoadProfile activeProfile = ModelLoadProfile.LIGHT;

    private volatile String cachedSystemBlock;
    private volatile int cachedSystemBlockKey;
    private volatile int cachedSystemPrefixTokens;
    private volatile boolean cancelRequested;

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void loadModel(Path modelPath) throws Exception {
        loadModel(modelPath, ModelLoadProfile.forPath(modelPath));
    }

    /** Loads with tier-specific ctx/batch/GPU settings (required for 32B on 24 GB unified memory). */
    public void loadModel(Path modelPath, ModelTier tier) throws Exception {
        loadModel(modelPath, ModelLoadProfile.forTier(tier, modelPath));
    }

    public void loadModel(Path modelPath, ModelLoadProfile profile) throws Exception {
        synchronized (loadLock) {
            if (status == Status.READY
                    && model != null
                    && modelPath.getFileName().toString().equals(modelName)
                    && activeProfile.equals(profile)) {
                return;
            }
            status = Status.LOADING;
            modelName = modelPath.getFileName().toString();
            activeProfile = profile;

            if (model != null) {
                model.close();
                model = null;
            }
            invalidateSystemCache();

            try {
                long loadStart = System.nanoTime();
                model = new LlamaModel(buildModelParams(modelPath, profile));
                cachedSystemPrefixTokens = 0;
                status = Status.READY;
                LOG.info("Model loaded in " + (System.nanoTime() - loadStart) / 1_000_000 + "ms: " + modelName
                        + " profile=" + profile.tier().name() + " ctx=" + profile.ctxSize());
            } catch (Exception e) {
                status = Status.ERROR;
                modelName = "None";
                throw e;
            }
        }

        var loadedName = modelName;
        executor.execute(() -> {
            try {
                var auditStore = new tech.rawden.ara.model.AuditLogStorage();
                var log = auditStore.load();
                log.addEntry(
                        new tech.rawden.ara.model.AuditLogEntry("MODEL_LOAD", "Loaded model: " + loadedName, null, 1));
                auditStore.save(log);
            } catch (Exception ignored) {
            }
        });
    }

    private static ModelParameters buildModelParams(Path modelPath, ModelLoadProfile profile) {
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(4, cores);
        int threadsBatch = Math.max(threads, cores);

        var params = new ModelParameters()
                .setModel(modelPath.toAbsolutePath().toString())
                .setGpuLayers(profile.gpuLayers())
                .setThreads(threads)
                .setThreadsBatch(threadsBatch)
                .setCtxSize(profile.ctxSize())
                .setBatchSize(profile.batchSize())
                .setUbatchSize(profile.ubatchSize())
                .setParallel(1)
                .enableFlashAttn()
                .setCacheTypeK(profile.cacheK())
                .setCacheTypeV(profile.cacheV())
                .setCacheReuse(256)
                .setPriority(2)
                .skipWarmup()
                .disablePerf();

        if (profile.tier() == ModelTier.LIGHT && shouldMlock()) {
            params.enableMlock();
        }
        if (shouldUseNuma()) {
            params.setNuma(NumaStrategy.DISTRIBUTE);
        }
        return params;
    }

    public ModelLoadProfile activeProfile() {
        return activeProfile;
    }

    private static boolean shouldMlock() {
        try {
            var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                long free = sun.getFreeMemorySize();
                return free > 6L * 1024 * 1024 * 1024;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean shouldUseNuma() {
        var osName = System.getProperty("os.name", "").toLowerCase();
        return !osName.contains("mac") && !osName.contains("darwin");
    }

    @Override
    public void unloadModel() {
        if (model != null) {
            model.close();
            model = null;
        }
        modelName = "None";
        status = Status.UNLOADED;
    }

    @Override
    public void generate(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        executor.execute(
                () -> runGenerationJob(userMessage, config, history, false, onToken, onComplete, onError, null));
    }

    @Override
    public void generateWithTools(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        executor.execute(
                () -> runGenerationJob(userMessage, config, history, true, onToken, onComplete, onError, onToolCall));
    }

    @Override
    public void cancelGeneration() {
        cancelRequested = true;
    }

    private void runGenerationJob(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            boolean withTools,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        cancelRequested = false;
        try {
            if (model == null) {
                onError.accept(new IllegalStateException("No model loaded"));
                return;
            }
            long buildStart = System.nanoTime();
            var fitted = fitPromptToContext(userMessage, config, history, withTools);
            long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
            LOG.info(String.format(
                    "Inference job: promptChars=%d, promptTokens=%d, ctx=%d, historyMsgs=%d, dropped=%d, buildMs=%d, tools=%s",
                    fitted.prompt().length(),
                    fitted.promptTokens(),
                    activeProfile.ctxSize(),
                    history.size(),
                    fitted.droppedMessages(),
                    buildMs,
                    withTools));
            runInference(fitted.prompt(), config, onToken, onComplete, onError, onToolCall);
        } catch (Exception e) {
            LOG.warning("Generation failed: " + e.getMessage());
            onError.accept(e);
        }
    }

    @Override
    public void warmup() {
        warmup(InferenceConfig.defaults());
    }

    @Override
    public void warmup(InferenceConfig config) {
        var cfg = config != null ? config : InferenceConfig.defaults();
        preparePromptCache(cfg);
        synchronized (loadLock) {
            if (model == null) {
                return;
            }
            try {
                long t0 = System.nanoTime();
                if (activeProfile.fullSystemWarmup()) {
                    var prefix = cachedSystemPrefix(cfg, true, 0);
                    refreshSystemPrefixTokens(prefix);
                    var warmupPrompt = prefix + "<|im_start|>user\nping<|im_end|>\n<|im_start|>assistant\n";
                    var params = inferenceParams(warmupPrompt, cfg).setNPredict(1).setTemperature(0f);
                    if (cachedSystemPrefixTokens > 0
                            && cachedSystemPrefixTokens < activeProfile.ctxSize() / 2) {
                        params.setNKeep(cachedSystemPrefixTokens);
                    }
                    for (LlamaOutput ignored : model.generate(params)) {
                        break;
                    }
                    LOG.info("Model warmup (system KV + 1 token, prefixTokens="
                            + cachedSystemPrefixTokens
                            + ") in "
                            + (System.nanoTime() - t0) / 1_000_000
                            + "ms");
                } else {
                    var params = new InferenceParameters("<|im_start|>assistant\n")
                            .setNPredict(1)
                            .setTemperature(0f);
                    for (LlamaOutput ignored : model.generate(params)) {
                        break;
                    }
                    LOG.info("Model warmup (heavy ping, ctx=" + activeProfile.ctxSize() + ") in "
                            + (System.nanoTime() - t0) / 1_000_000
                            + "ms");
                }
            } catch (Exception e) {
                LOG.warning("Model warmup failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void preparePromptCache(InferenceConfig config) {
        var cfg = config != null ? config : InferenceConfig.defaults();
        buildStaticSystemContent(cfg, true, activeProfile);
    }

    private record FittedPrompt(String prompt, int promptTokens, int droppedMessages) {}

    private FittedPrompt fitPromptToContext(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            boolean withTools) {
        int contextBudget = Math.min(config.maxContextChars(), activeProfile.maxContextChars());
        var limited = PromptContextLimiter.limit(history, contextBudget);
        int outputReserve = Math.min(config.maxTokens(), activeProfile.maxGenerateTokens());
        int maxPromptTokens = activeProfile.ctxSize() - outputReserve - 32;

        String prompt = buildPrompt(userMessage, config, limited.history(), withTools, limited.droppedMessages());
        int promptTokens = countPromptTokens(prompt);
        int dropped = limited.droppedMessages();

        var working = new java.util.ArrayList<>(limited.history());
        while (promptTokens > maxPromptTokens
                && working.size() > PromptContextLimiter.MIN_RECENT_MESSAGES) {
            working.remove(0);
            dropped++;
            prompt = buildPrompt(userMessage, config, working, withTools, dropped);
            promptTokens = countPromptTokens(prompt);
        }

        if (promptTokens > maxPromptTokens) {
            LOG.warning(String.format(
                    "Prompt still exceeds KV budget after truncation: tokens=%d max=%d ctx=%d — inference may fail",
                    promptTokens, maxPromptTokens, activeProfile.ctxSize()));
        }
        if (dropped > limited.droppedMessages()) {
            LOG.info("Token budget truncation: dropped " + dropped + " oldest messages");
        }
        return new FittedPrompt(prompt, promptTokens, dropped);
    }

    private int countPromptTokens(String prompt) {
        if (model == null) {
            return prompt.length() / 4;
        }
        try {
            return model.encode(prompt).length;
        } catch (Exception e) {
            LOG.warning("Could not tokenize prompt: " + e.getMessage());
            return prompt.length() / 4;
        }
    }

    private String buildPrompt(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            boolean withTools,
            int droppedMessages) {
        var sb = new StringBuilder();
        sb.append(cachedSystemPrefix(config, withTools, droppedMessages));
        for (var msg : history) {
            var role =
                    switch (msg.role()) {
                        case USER -> "user";
                        case ASSISTANT -> "assistant";
                        case SYSTEM -> "system";
                        case TOOL -> "tool";
                    };
            sb.append("<|im_start|>").append(role).append("\n");
            if (msg.role() == ChatMessage.Role.TOOL) {
                sb.append("<|tool_result|>\n");
            }
            sb.append(msg.content());
            sb.append("<|im_end|>\n");
        }
        sb.append("<|im_start|>user\n").append(userMessage).append("<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private String cachedSystemPrefix(InferenceConfig config, boolean withTools, int droppedMessages) {
        return "<|im_start|>system\n" + buildSystemBlock(config, withTools, droppedMessages) + "<|im_end|>\n";
    }

    private void refreshSystemPrefixTokens(String systemPrefix) {
        if (model == null) {
            return;
        }
        try {
            cachedSystemPrefixTokens = model.encode(systemPrefix).length;
            LOG.info("Static system prefix: " + cachedSystemPrefixTokens + " tokens, " + systemPrefix.length()
                    + " chars");
        } catch (Exception e) {
            LOG.warning("Could not tokenize system prefix: " + e.getMessage());
        }
    }

    private String buildSystemBlock(InferenceConfig config, boolean withTools, int droppedMessages) {
        int key = systemBlockKey(config, withTools);
        String base;
        if (cachedSystemBlock != null && cachedSystemBlockKey == key) {
            base = cachedSystemBlock;
        } else {
            base = buildStaticSystemContent(config, withTools, activeProfile);
            cachedSystemBlock = base;
            cachedSystemBlockKey = key;
            cachedSystemPrefixTokens = 0;
        }

        if (droppedMessages > 0) {
            base += "\n\n[Context note: " + droppedMessages
                    + " older messages were omitted from this prompt to stay within the context budget.]";
        }
        base += "\n\nCurrent date/time: " + LocalDateTime.now().format(CLOCK_FORMAT) + ".";
        return base;
    }

    private static String buildStaticSystemContent(
            InferenceConfig config, boolean withTools, ModelLoadProfile profile) {
        var system = config.systemPrompt() != null && !config.systemPrompt().isBlank()
                ? config.systemPrompt()
                : InferenceConfig.DEFAULT_SYSTEM_PROMPT;

        if (profile.compactPrompt()) {
            system += "\n\nVex agent tools use ara-tool names in <|tool_call|> (not protocol IDs).";
        } else {
            system += VexProtocolCatalog.formatCatalogSection();
        }

        if (withTools) {
            var policy = profile.compactPrompt() ? TOOL_POLICY_COMPACT : TOOL_POLICY;
            system += "\n\n" + policy + "\n\nTools:\n"
                    + ToolCall.getFunctionDefinitions(
                            config.webSearchEnabled(), config.contextMemoryEnabled(), config.terminalEnabled())
                    + "\n\nTool call format: <|tool_call|>{\"name\":\"<ara-tool>\",\"arguments\":{...}}<|im_end|>";
        }
        return system;
    }

    private InferenceParameters inferenceParams(String prompt, InferenceConfig config) {
        int nPredict = Math.min(config.maxTokens(), activeProfile.maxGenerateTokens());
        return new InferenceParameters(prompt)
                .setTemperature(config.temperature())
                .setNPredict(nPredict)
                .setCachePrompt(true);
    }

    private int systemBlockKey(InferenceConfig config, boolean withTools) {
        var clockBucket = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return Objects.hash(
                config.systemPrompt(),
                config.webSearchEnabled(),
                config.contextMemoryEnabled(),
                config.terminalEnabled(),
                withTools,
                activeProfile.compactPrompt(),
                VexProtocolCatalog.protocols().size(),
                clockBucket);
    }

    /** Clears cached system prompt (e.g. after Vex protocol reload). */
    public void invalidateSystemCache() {
        cachedSystemBlock = null;
        cachedSystemBlockKey = 0;
        cachedSystemPrefixTokens = 0;
    }

    private void runInference(
            String prompt,
            InferenceConfig config,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        synchronized (loadLock) {
            runInferenceLocked(prompt, config, onToken, onComplete, onError, onToolCall);
        }
    }

    private void runInferenceLocked(
            String prompt,
            InferenceConfig config,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        try {
            if (cachedSystemPrefixTokens <= 0) {
                refreshSystemPrefixTokens(cachedSystemPrefix(config, true, 0));
            }

            long inferStart = System.nanoTime();
            long firstTokenMs = -1;
            int tokenCount = 0;

            var inferParams = inferenceParams(prompt, config);
            if (cachedSystemPrefixTokens > 0
                    && cachedSystemPrefixTokens < activeProfile.ctxSize() / 2) {
                inferParams.setNKeep(cachedSystemPrefixTokens);
            }

            var iter = model.generate(inferParams).iterator();
            var buffer = new StringBuilder();
            int lastStreamed = 0;
            boolean toolCallFound = false;

            while (iter.hasNext()) {
                if (cancelRequested) {
                    cancelRequested = false;
                    onError.accept(new java.util.concurrent.CancellationException("Generation stopped"));
                    return;
                }
                var token = iter.next().toString();
                tokenCount++;
                if (firstTokenMs < 0) {
                    firstTokenMs = (System.nanoTime() - inferStart) / 1_000_000;
                }
                buffer.append(token);

                var fullText = buffer.toString();
                int toolCallIdx = withToolCallIndex(fullText);

                if (toolCallIdx >= 0 && onToolCall != null) {
                    if (toolCallIdx > lastStreamed) {
                        onToken.accept(fullText.substring(lastStreamed, toolCallIdx));
                    }

                    var afterMarker = fullText.substring(toolCallIdx + "<|tool_call|>".length());
                    var jsonBuf = new StringBuilder(afterMarker);

                    while (iter.hasNext()) {
                        var innerToken = iter.next().toString();
                        tokenCount++;
                        var endIdx = innerToken.indexOf("<|im_end|>");
                        if (endIdx >= 0) {
                            jsonBuf.append(innerToken.substring(0, endIdx));
                            break;
                        }
                        jsonBuf.append(innerToken);
                    }

                    var toolCall = ToolCall.parse(jsonBuf.toString().trim());
                    if (toolCall != null) {
                        toolCallFound = true;
                        onToolCall.accept(toolCall);
                    }
                    break;
                }

                // Stop streaming to UI once a partial tool-call marker appears
                int partialIdx = fullText.indexOf("<|tool");
                if (partialIdx >= 0) {
                    if (partialIdx > lastStreamed) {
                        onToken.accept(fullText.substring(lastStreamed, partialIdx));
                    }
                    lastStreamed = partialIdx;
                    continue;
                }

                onToken.accept(fullText.substring(lastStreamed));
                lastStreamed = buffer.length();
            }

            long totalMs = (System.nanoTime() - inferStart) / 1_000_000;
            LOG.info(String.format(
                    "Inference done: prefillMs=%d, totalMs=%d, tokens=%d, toolCall=%s",
                    firstTokenMs, totalMs, tokenCount, toolCallFound));

            if (!toolCallFound) {
                onComplete.run();
            }
        } catch (Exception e) {
            LOG.warning("Inference failed: " + e.getMessage());
            onError.accept(e);
        }
    }

    private static int withToolCallIndex(String text) {
        return text.indexOf("<|tool_call|>");
    }

    @Override
    public void generateTitle(String message, Consumer<String> onTitle) {
        executor.execute(() -> {
            synchronized (loadLock) {
                try {
                    if (model == null) {
                        var fallback = message.length() > 45 ? message.substring(0, 42) + "..." : message;
                        onTitle.accept(fallback);
                        return;
                    }

                    var prompt =
                            "Generate a very short title (5 words max) for a conversation that starts with this message. Reply with ONLY the title, no punctuation:\n\n"
                                    + message;

                    var inferParams = new InferenceParameters(prompt)
                            .setTemperature(0.3f)
                            .setNPredict(15); // title gen is small & fast by design

                    var sb = new StringBuilder();
                    for (LlamaOutput output : model.generate(inferParams)) {
                        sb.append(output.toString());
                    }
                    var title = sb.toString().trim();
                    title = title.replaceAll("[\"']", "").trim();
                    if (title.length() > 60) title = title.substring(0, 57) + "...";
                    onTitle.accept(title);
                } catch (Exception e) {
                    LOG.warning("Title generation failed: " + e.getMessage());
                    var fallback = message.length() > 45 ? message.substring(0, 42) + "..." : message;
                    onTitle.accept(fallback);
                }
            }
        });
    }

    /**
     * Cheap routing classification on the currently loaded (light) model. Returns trimmed uppercase text.
     * Must be called while the light model is loaded and under the same {@link #loadLock} as inference.
     */
    public String quickClassify(String prompt, int maxTokens) {
        synchronized (loadLock) {
            if (model == null) {
                return "LIGHT";
            }
            try {
                var inferParams = new InferenceParameters(prompt)
                        .setTemperature(0f)
                        .setNPredict(Math.max(1, maxTokens));
                var sb = new StringBuilder();
                for (LlamaOutput output : model.generate(inferParams)) {
                    sb.append(output.toString());
                    if (sb.length() > 32) {
                        break;
                    }
                }
                return sb.toString().trim().toUpperCase();
            } catch (Exception e) {
                LOG.warning("Routing classification failed: " + e.getMessage());
                return "LIGHT";
            }
        }
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
        unloadModel();
    }
}
