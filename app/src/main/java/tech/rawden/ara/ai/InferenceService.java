package tech.rawden.ara.ai;

import tech.rawden.ara.model.ChatMessage;
import tech.rawden.ara.model.InferenceConfig;
import tech.rawden.ara.tool.ToolCall;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Abstraction over on-device LLM inference. {@link LlamaCppInferenceService} is the production
 * implementation; {@link DummyInferenceService} is a no-op stub for headless tests.
 */
public interface InferenceService {

    enum Status {
        UNLOADED,
        LOADING,
        READY,
        ERROR
    }

    Status status();

    void loadModel(Path modelPath) throws Exception;

    default void loadModel(String modelName) throws Exception {
        throw new UnsupportedOperationException();
    }

    void unloadModel();

    void generate(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError);

    default void generateWithTools(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Consumer<ToolCall> onToolCall) {
        generate(userMessage, config, history, onToken, onComplete, onError);
    }

    String modelName();

    default void generateTitle(String message, Consumer<String> onTitle) {
        onTitle.accept(message.length() > 45 ? message.substring(0, 42) + "..." : message);
    }

    /** Optional one-token warmup after load to avoid a cold first user inference. */
    default void warmup() {}

    /** Request cancellation of the active generation (Vex protocol 10 kill analogue). */
    default void cancelGeneration() {}

    void shutdown();
}
