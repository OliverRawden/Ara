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

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Object loadLock = new Object();

    private volatile Status status = Status.UNLOADED;
    private volatile String modelName = "None";
    private volatile LlamaModel model;

    private volatile String cachedSystemBlock;
    private volatile int cachedSystemBlockKey;
    private volatile boolean cancelRequested;

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void loadModel(Path modelPath) throws Exception {
        synchronized (loadLock) {
            if (status == Status.READY
                    && model != null
                    && modelPath.getFileName().toString().equals(modelName)) {
                return;
            }
            status = Status.LOADING;
            modelName = modelPath.getFileName().toString();

            if (model != null) {
                model.close();
                model = null;
            }

            try {
                var modelParams = new ModelParameters()
                        .setModel(modelPath.toAbsolutePath().toString())
                        .setGpuLayers(99)
                        .setThreads(Math.max(4, Runtime.getRuntime().availableProcessors() - 2))
                        .enableFlashAttn()
                        .setCacheTypeK(CacheType.Q8_0)
                        .setCacheTypeV(CacheType.Q4_0)
                        .setNuma(NumaStrategy.DISTRIBUTE)
                        .skipWarmup();
                model = new LlamaModel(modelParams);
                status = Status.READY;
                LOG.info("Model loaded: " + modelName);
            } catch (Exception e) {
                status = Status.ERROR;
                modelName = "None";
                throw e;
            }
        }

        try {
            var auditStore = new tech.rawden.ara.model.AuditLogStorage();
            var log = auditStore.load();
            log.addEntry(new tech.rawden.ara.model.AuditLogEntry("MODEL_LOAD", "Loaded model: " + modelName, null, 1));
            auditStore.save(log);
        } catch (Exception ignored) {
        }
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
        executor.execute(() -> runGenerationJob(userMessage, config, history, false, onToken, onComplete, onError, null));
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
        executor.execute(() ->
                runGenerationJob(userMessage, config, history, true, onToken, onComplete, onError, onToolCall));
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
            var limited = PromptContextLimiter.limit(history, config.maxContextChars());
            if (limited.droppedMessages() > 0) {
                LOG.info("Context truncated: dropped " + limited.droppedMessages() + " oldest messages");
            }
            var prompt = buildPrompt(userMessage, config, limited.history(), withTools, limited.droppedMessages());
            long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
            LOG.info(String.format(
                    "Inference job: promptChars=%d, historyMsgs=%d, dropped=%d, buildMs=%d, tools=%s",
                    prompt.length(), history.size(), limited.droppedMessages(), buildMs, withTools));
            runInference(prompt, config, onToken, onComplete, onError, onToolCall);
        } catch (Exception e) {
            LOG.warning("Generation failed: " + e.getMessage());
            onError.accept(e);
        }
    }

    @Override
    public void warmup() {
        synchronized (loadLock) {
            if (model == null) {
                return;
            }
            try {
                long t0 = System.nanoTime();
                var params = new InferenceParameters(
                                "<|im_start|>user\nping<|im_end|>\n<|im_start|>assistant\n")
                        .setNPredict(1)
                        .setTemperature(0f)
                        .setCachePrompt(true);
                for (LlamaOutput ignored : model.generate(params)) {
                    break;
                }
                LOG.info("Model warmup (1 token) in " + (System.nanoTime() - t0) / 1_000_000 + "ms");
            } catch (Exception e) {
                LOG.warning("Model warmup failed: " + e.getMessage());
            }
        }
    }

    private String buildPrompt(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            boolean withTools,
            int droppedMessages) {
        var now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm"));
        var system = buildSystemBlock(config, withTools, now, droppedMessages);

        var sb = new StringBuilder();
        sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n");
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

    private String buildSystemBlock(InferenceConfig config, boolean withTools, String now, int droppedMessages) {
        int key = systemBlockKey(config, withTools);
        String base;
        if (cachedSystemBlock != null && cachedSystemBlockKey == key) {
            base = cachedSystemBlock;
        } else {
            base = buildStaticSystemContent(config, withTools);
            cachedSystemBlock = base;
            cachedSystemBlockKey = key;
        }

        var block = base + "\n\nThe current date and time is " + now + ".";
        if (droppedMessages > 0) {
            block += "\n\n[Context note: " + droppedMessages
                    + " older messages were omitted from this prompt to stay within the context budget.]";
        }
        return block;
    }

    private static String buildStaticSystemContent(InferenceConfig config, boolean withTools) {
        var system = config.systemPrompt() != null && !config.systemPrompt().isBlank()
                ? config.systemPrompt()
                : InferenceConfig.DEFAULT_SYSTEM_PROMPT;

        system += VexProtocolCatalog.formatCatalogSection();

        if (withTools) {
            system +=
                    "\n\nYou have access to the following agent tools (each maps to a Vex ara-tool protocol above):\n\n"
                            + ToolCall.getFunctionDefinitions(
                                    config.webSearchEnabled(), config.contextMemoryEnabled(), config.terminalEnabled())
                            + "\n\nWhen you need to call a tool, output ONLY the tool call (no preamble text):\n<|tool_call|>{\"name\": \"<ara-tool-name>\", \"arguments\": {...}}<|im_end|>\n"
                            + "Use the ara-tool name (e.g. get_current_datetime), not the protocol ID (e.g. 102).\n"
                            + "Then wait for the result before responding naturally.\n\n"
                            + "Persistent memory: ~/Documents/Ara/context.md — read_memory (104) at session start; write_memory (105) / append_memory (106) to update.";
        }
        return system;
    }

    private static int systemBlockKey(InferenceConfig config, boolean withTools) {
        return Objects.hash(
                config.systemPrompt(),
                config.webSearchEnabled(),
                config.contextMemoryEnabled(),
                config.terminalEnabled(),
                withTools,
                VexProtocolCatalog.protocols().size());
    }

    /** Clears cached system prompt (e.g. after Vex protocol reload). */
    public void invalidateSystemCache() {
        cachedSystemBlock = null;
        cachedSystemBlockKey = 0;
    }

    private void runInference(
            String prompt,
            InferenceConfig config,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        try {
            long inferStart = System.nanoTime();
            long firstTokenMs = -1;
            int tokenCount = 0;

            var inferParams = new InferenceParameters(prompt)
                    .setTemperature(config.temperature())
                    .setNPredict(config.maxTokens())
                    .setCachePrompt(true);

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
        });
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
