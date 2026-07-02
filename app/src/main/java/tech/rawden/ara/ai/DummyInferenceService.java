package tech.rawden.ara.ai;

import tech.rawden.ara.model.ChatMessage;
import tech.rawden.ara.model.InferenceConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class DummyInferenceService implements InferenceService {

    private static final Logger LOG = Logger.getLogger(DummyInferenceService.class.getName());

    private Status status = Status.UNLOADED;
    private String modelName = "None";
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void loadModel(Path modelPath) throws Exception {
        status = Status.LOADING;
        LOG.info("Loading model from: " + modelPath);
        modelName = modelPath.getFileName().toString();
        Thread.sleep(500);
        status = Status.READY;
        LOG.info("Model loaded: " + modelName);
    }

    @Override
    public void loadModel(String modelName) throws Exception {
        this.modelName = modelName;
        status = Status.READY;
        LOG.info("Model selected: " + modelName);
    }

    @Override
    public void unloadModel() {
        status = Status.UNLOADED;
        modelName = "None";
    }

    @Override
    public void generate(
            String userMessage,
            InferenceConfig config,
            List<ChatMessage> history,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        executor.execute(() -> {
            try {
                String response = "This is a placeholder response from the Ara AI assistant.\n\n"
                        + "To enable real inference, download a GGUF model and load it.\n\n"
                        + "Your message was: " + userMessage;

                for (char c : response.toCharArray()) {
                    onToken.accept(String.valueOf(c));
                    Thread.sleep(10);
                }
                onComplete.run();
            } catch (Exception e) {
                onError.accept(e);
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
        status = Status.UNLOADED;
    }
}
