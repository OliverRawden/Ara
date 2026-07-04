package tech.rawden.ara.ai;

import tech.rawden.ara.model.ChatMessage;
import tech.rawden.ara.model.InferenceConfig;
import tech.rawden.ara.tool.ToolCall;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import tech.rawden.ara.core.AppLog;

import java.util.logging.Logger;

/**
 * {@link InferenceService} facade that routes each request through {@link ModelRouter} before
 * delegating to {@link LlamaCppInferenceService}.
 */
public final class RoutingInferenceService implements InferenceService {

    private static final Logger LOG = AppLog.of("routing");

    private final LlamaCppInferenceService backend;
    private final ModelRouter router;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RoutingInferenceService(LlamaCppInferenceService backend, ModelRouter router) {
        this.backend = backend;
        this.router = router;
    }

    public ModelRouter router() {
        return router;
    }

    public LlamaCppInferenceService backend() {
        return backend;
    }

    @Override
    public Status status() {
        return backend.status();
    }

    @Override
    public void loadModel(Path modelPath) throws Exception {
        backend.loadModel(modelPath);
    }

    @Override
    public void unloadModel() {
        backend.unloadModel();
    }

    @Override
    public void generate(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        executor.execute(() -> runRouted(userMessage, config, history, false, onToken, onComplete, onError, null));
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
        executor.execute(() -> runRouted(userMessage, config, history, true, onToken, onComplete, onError, onToolCall));
    }

    private void runRouted(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            boolean withTools,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        try {
            LOG.fine("Routed request: tools=" + withTools + ", promptChars=" + (userMessage != null ? userMessage.length() : 0)
                    + ", historyMsgs=" + (history != null ? history.size() : 0));
            var summary = ModelRouter.buildContextSummary(history);
            router.prepareForRequest(userMessage, summary);
            if (withTools) {
                backend.generateWithTools(userMessage, config, history, onToken, onComplete, onError, onToolCall);
            } else {
                backend.generate(userMessage, config, history, onToken, onComplete, onError);
            }
        } catch (Exception e) {
            LOG.warning("Routed inference failed: " + e.getMessage());
            onError.accept(e);
        }
    }

    @Override
    public void cancelGeneration() {
        backend.cancelGeneration();
    }

    @Override
    public void warmup() {
        backend.warmup();
    }

    @Override
    public void warmup(InferenceConfig config) {
        backend.warmup(config);
    }

    @Override
    public void preparePromptCache(InferenceConfig config) {
        backend.preparePromptCache(config);
    }

    @Override
    public void generateTitle(String message, Consumer<String> onTitle) {
        executor.execute(() -> {
            try {
                router.ensureLightModel();
                backend.generateTitle(message, onTitle);
            } catch (Exception e) {
                LOG.warning("Title generation routing failed: " + e.getMessage());
                onTitle.accept(message.length() > 45 ? message.substring(0, 42) + "..." : message);
            }
        });
    }

    @Override
    public String modelName() {
        return backend.modelName();
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
        router.shutdown();
        backend.shutdown();
    }
}