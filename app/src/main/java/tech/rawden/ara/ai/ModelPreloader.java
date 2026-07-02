package tech.rawden.ara.ai;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Background GGUF model preloader.
 *
 * <p>Started once after the main window is visible and any encryption unlock has completed.
 * Runs on a dedicated virtual thread ({@code ara-model-preloader}) so tensor mmap / GPU init
 * never blocks the JavaFX thread or competes with PBKDF2 / chat deserialization.
 *
 * <p>{@link tech.rawden.ara.ui.ChatViewComp} calls {@link #whenReady(Runnable, Consumer)} on
 * send — if preload already finished, the first message responds immediately; if still loading,
 * it waits for the same in-flight task instead of starting a duplicate load.
 */
public final class ModelPreloader {

    private static final Logger LOG = Logger.getLogger(ModelPreloader.class.getName());

    private final InferenceService inferenceService;
    private final ModelManager modelManager;

    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> loadTask = CompletableFuture.completedFuture(null);
    private volatile String preferredModelFilename = "";

    public ModelPreloader(InferenceService inferenceService, ModelManager modelManager) {
        this.inferenceService = inferenceService;
        this.modelManager = modelManager;
    }

    /**
     * Schedule a one-shot background preload. Safe to call multiple times — only the first call
     * starts work. Does nothing if a model is already {@link InferenceService.Status#READY}.
     */
    public void schedulePreload(String preferredModelFilename) {
        if (inferenceService.status() == InferenceService.Status.READY) {
            scheduled.set(true);
            return;
        }
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        this.preferredModelFilename =
                preferredModelFilename != null ? preferredModelFilename : "";

        loadTask = CompletableFuture.runAsync(this::runPreload, runnable -> Thread.ofVirtual()
                .name("ara-model-preloader")
                .start(runnable));
        LOG.info("Scheduled background model preload");
    }

    /**
     * Ensure a model is loaded, scheduling preload first if needed. Invokes {@code onReady} on
     * the calling thread once the model is ready; {@code onError} if preload failed or no GGUF
     * files exist.
     */
    public void whenReady(Runnable onReady, Consumer<Throwable> onError) {
        if (inferenceService.status() == InferenceService.Status.READY) {
            onReady.run();
            return;
        }
        if (!scheduled.get()) {
            schedulePreload(preferredModelFilename);
        }
        loadTask.whenComplete((ignored, err) -> {
            if (err != null) {
                onError.accept(err instanceof java.util.concurrent.CompletionException
                                && err.getCause() != null
                        ? err.getCause()
                        : err);
                return;
            }
            if (inferenceService.status() == InferenceService.Status.READY) {
                onReady.run();
            } else {
                onError.accept(new IllegalStateException(
                        "No local GGUF model available. Download one in Settings → Model."));
            }
        });
    }

    public boolean isScheduled() {
        return scheduled.get();
    }

    private void runPreload() {
        try {
            if (inferenceService.status() == InferenceService.Status.READY) {
                return;
            }
            Optional<Path> model = modelManager.resolveModel(preferredModelFilename);
            if (model.isEmpty()) {
                LOG.info("Background preload skipped — no .gguf files in " + modelManager.modelsDirectory());
                return;
            }
            LOG.info("Background preloading model: " + model.get().getFileName());
            inferenceService.loadModel(model.get());
            inferenceService.warmup();
            LOG.info("Background model preload + warmup complete");
        } catch (Exception e) {
            LOG.warning("Background model preload failed: " + e.getMessage());
            throw new java.util.concurrent.CompletionException(e);
        }
    }
}