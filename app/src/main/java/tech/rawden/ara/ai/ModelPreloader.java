package tech.rawden.ara.ai;

import tech.rawden.ara.model.InferenceConfig;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Background GGUF model preloader.
 *
 * <p>Started as soon as the inference service exists (before encryption unlock / chat decrypt).
 * Runs on a dedicated virtual thread ({@code ara-model-preloader}) so tensor mmap / GPU init
 * never blocks the JavaFX thread or competes with PBKDF2 / chat deserialization.
 *
 * <p>Preloads the <em>light</em> model by default so routing can escalate to heavy on demand.
 */
public final class ModelPreloader {

    private static final Logger LOG = Logger.getLogger(ModelPreloader.class.getName());

    private final InferenceService inferenceService;
    private final ModelManager modelManager;
    private final InferenceConfig inferenceConfig;
    private final ModelRouter modelRouter;

    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean warmupScheduled = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> loadTask = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<Void> warmupTask = CompletableFuture.completedFuture(null);
    private volatile String preferredModelFilename = "";

    public ModelPreloader(
            InferenceService inferenceService, ModelManager modelManager, InferenceConfig inferenceConfig) {
        this(inferenceService, modelManager, inferenceConfig, null);
    }

    public ModelPreloader(
            InferenceService inferenceService,
            ModelManager modelManager,
            InferenceConfig inferenceConfig,
            ModelRouter modelRouter) {
        this.inferenceService = inferenceService;
        this.modelManager = modelManager;
        this.inferenceConfig = inferenceConfig;
        this.modelRouter = modelRouter;
    }

    /**
     * Schedule a one-shot background preload. Safe to call multiple times — only the first call
     * starts work. Does nothing if a model is already {@link InferenceService.Status#READY}.
     */
    public void schedulePreload(String preferredModelFilename) {
        if (inferenceService.status() == InferenceService.Status.READY) {
            scheduled.set(true);
            scheduleWarmup();
            return;
        }
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }
        this.preferredModelFilename = preferredModelFilename != null ? preferredModelFilename : "";

        loadTask = CompletableFuture.runAsync(
                this::runPreload,
                runnable -> Thread.ofVirtual().name("ara-model-preloader").start(runnable));
        LOG.info("Scheduled background light model preload");
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
        loadTask.whenComplete((ignored, err) -> handleTaskComplete(err, onReady, onError));
    }

    /**
     * Waits for model load <em>and</em> KV-cache warmup before the first token. Warmup primes the
     * static system prompt so chat TTFT is ~1s instead of re-prefilling ~500 tokens cold.
     */
    public void whenInferenceReady(Runnable onReady, Consumer<Throwable> onError) {
        Runnable afterWarmup =
                () -> warmupTask.whenComplete((ignored, err) -> handleTaskComplete(err, onReady, onError));
        if (inferenceService.status() == InferenceService.Status.READY) {
            afterWarmup.run();
        } else {
            whenReady(afterWarmup, onError);
        }
    }

    private void handleTaskComplete(Throwable err, Runnable onReady, Consumer<Throwable> onError) {
        if (err != null) {
            onError.accept(
                    err instanceof java.util.concurrent.CompletionException && err.getCause() != null
                            ? err.getCause()
                            : err);
            return;
        }
        if (inferenceService.status() == InferenceService.Status.READY) {
            onReady.run();
        } else {
            onError.accept(
                    new IllegalStateException("No local GGUF model available. Download one in Settings → Model."));
        }
    }

    public boolean isScheduled() {
        return scheduled.get();
    }

    private void runPreload() {
        try {
            if (inferenceService.status() == InferenceService.Status.READY) {
                return;
            }
            if (modelRouter != null) {
                LOG.info("Background preloading light model via router");
                long t0 = System.nanoTime();
                modelRouter.ensureLightModel();
                LOG.info("Background light model load complete in " + (System.nanoTime() - t0) / 1_000_000 + "ms");
                scheduleWarmup();
                return;
            }

            Optional<Path> model = modelManager.resolveModel(preferredModelFilename);
            if (model.isEmpty()) {
                LOG.info("Background preload skipped — no .gguf files in " + modelManager.modelsDirectory());
                return;
            }
            LOG.info("Background preloading model: " + model.get().getFileName());
            long t0 = System.nanoTime();
            inferenceService.preparePromptCache(inferenceConfig);
            inferenceService.loadModel(model.get());
            LOG.info("Background model load complete in " + (System.nanoTime() - t0) / 1_000_000 + "ms");
            scheduleWarmup();
        } catch (Exception e) {
            LOG.warning("Background model preload failed: " + e.getMessage());
            throw new java.util.concurrent.CompletionException(e);
        }
    }

    private void scheduleWarmup() {
        if (!warmupScheduled.compareAndSet(false, true)) {
            return;
        }
        warmupTask = CompletableFuture.runAsync(
                () -> {
                    inferenceService.warmup(inferenceConfig);
                    LOG.info("Background KV-cache warmup complete");
                },
                runnable -> Thread.ofVirtual().name("ara-model-warmup").start(runnable));
    }
}