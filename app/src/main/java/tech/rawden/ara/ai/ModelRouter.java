package tech.rawden.ara.ai;

import tech.rawden.ara.model.AppSettings;
import tech.rawden.ara.model.InferenceConfig;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * On-device multi-model router: light ~7B stays hot; heavy ~30B loads on demand for complex work.
 * Integrates with {@link RoutingInferenceService} before each inference or Vex tool round.
 */
public final class ModelRouter {

    private static final Logger LOG = Logger.getLogger(ModelRouter.class.getName());

    private static final long HEAVY_IDLE_UNLOAD_MINUTES = 10;

    private static final Pattern HEAVY_KEYWORDS = Pattern.compile(
            "(?i)\\b("
                    + "implement|refactor|debug|architect|design pattern|multi[- ]step|"
                    + "write code|generate code|fix (the |this )?bug|unit test|integration test|"
                    + "sql query|api endpoint|class diagram|algorithm|optimi[sz]e|"
                    + "tool call|function call|mcp|vex protocol|execute_command|"
                    + "full (app|application|project)|from scratch|code review|"
                    + "explain (this|the) code|parse|regex|compile|deploy"
                    + ")\\b");

    private final LlamaCppInferenceService backend;
    private final ModelManager modelManager;
    private final AppSettings appSettings;
    private final InferenceConfig inferenceConfig;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "ara-heavy-unload");
        t.setDaemon(true);
        return t;
    });

    private volatile RoutingMode userOverride = RoutingMode.AUTO;
    private volatile RoutingMode singleTurnOverride;
    private volatile ModelTier activeTier = ModelTier.LIGHT;
    private volatile boolean lastEscalationAuto = false;
    private volatile ScheduledFuture<?> heavyUnloadTask;

    private final StringProperty badgeLabel = new SimpleStringProperty(ModelTier.LIGHT.badgeLabel());
    private final StringProperty badgeDetail = new SimpleStringProperty("Auto routing enabled");
    private final BooleanProperty escalatedThisTurn = new SimpleBooleanProperty(false);
    private final ObjectProperty<RoutingMode> routingModeProperty = new SimpleObjectProperty<>(RoutingMode.AUTO);
    private final ObjectProperty<ModelTier> activeTierProperty = new SimpleObjectProperty<>(ModelTier.LIGHT);

    public ModelRouter(
            LlamaCppInferenceService backend,
            ModelManager modelManager,
            AppSettings appSettings,
            InferenceConfig inferenceConfig) {
        this.backend = backend;
        this.modelManager = modelManager;
        this.appSettings = appSettings;
        this.inferenceConfig = inferenceConfig;
        userOverride = appSettings.getRoutingMode();
        routingModeProperty.set(userOverride);
        updateBadge();
    }

    public record RoutingDecision(ModelTier tier, boolean autoEscalated, String reason) {}

    /** Select tier and ensure the correct GGUF is loaded and warmed. */
    public RoutingDecision prepareForRequest(String prompt, String contextSummary) throws Exception {
        if (prompt == null || prompt.isBlank()) {
            if (activeTier == ModelTier.HEAVY) {
                ensureHeavyModel();
            } else {
                ensureLightModel();
            }
            return new RoutingDecision(activeTier, false, "Tool round continuation");
        }

        RoutingMode effective = singleTurnOverride != null ? singleTurnOverride : userOverride;
        boolean forceHeavy = effective == RoutingMode.HEAVY_ONLY;
        boolean forceLight = effective == RoutingMode.LIGHT_ONLY;

        RoutingDecision decision;
        if (forceLight) {
            decision = new RoutingDecision(ModelTier.LIGHT, false, "User-forced light");
            ensureLightModel();
        } else {
            boolean needsHeavy = forceHeavy
                    || keywordHeuristicTriggersHeavy(prompt)
                    || (effective == RoutingMode.AUTO && runLightModelClassification(prompt, contextSummary));
            if (needsHeavy) {
                String reason = forceHeavy
                        ? "User-forced heavy"
                        : keywordHeuristicTriggersHeavy(prompt)
                                ? "Keyword heuristic"
                                : "Light model classified as complex";
                decision = new RoutingDecision(ModelTier.HEAVY, !forceHeavy, reason);
                ensureHeavyModel();
            } else {
                decision = new RoutingDecision(ModelTier.LIGHT, false, "Light model sufficient");
                ensureLightModel();
            }
        }

        activeTier = decision.tier();
        lastEscalationAuto = decision.autoEscalated();
        escalatedThisTurn.set(decision.tier() == ModelTier.HEAVY && decision.autoEscalated());
        activeTierProperty.set(decision.tier());
        updateBadge();
        LOG.info("Routing: tier=" + decision.tier() + ", reason=" + decision.reason());

        if (singleTurnOverride != null) {
            singleTurnOverride = null;
        }
        return decision;
    }

    public void ensureLightModel() throws Exception {
        cancelHeavyUnload();
        String filename = resolveLightFilename();
        Path path = modelManager.resolveModel(filename).orElse(modelManager.defaultModelPath());
        if (!java.nio.file.Files.exists(path)) {
            throw new IllegalStateException(
                    "Light model not found. Download one in Settings → Model.");
        }
        if (activeTier == ModelTier.HEAVY && backend.status() == InferenceService.Status.READY) {
            backend.unloadModel();
        }
        loadAndWarm(path, ModelTier.LIGHT);
        activeTier = ModelTier.LIGHT;
        activeTierProperty.set(ModelTier.LIGHT);
        updateBadge();
    }

    public void ensureHeavyModel() throws Exception {
        cancelHeavyUnload();
        String filename = resolveHeavyFilename();
        Path path = modelManager.heavyModelPath(filename);
        if (!java.nio.file.Files.exists(path)) {
            if (appSettings.isDownloadHeavyFromRepo() && modelManager.isHeavyDownloadAvailable()) {
                LOG.info("Heavy model missing — starting download from Ara repo");
                modelManager.downloadHeavyModel(null);
                path = modelManager.heavyModelPath(filename);
            } else {
                throw new IllegalStateException(
                        "Heavy model not found at "
                                + path
                                + ". Download it in Settings or place the GGUF in "
                                + modelManager.modelsDirectory());
            }
        }
        releaseMemoryBeforeHeavyLoad();
        loadAndWarm(path, ModelTier.HEAVY);
        activeTier = ModelTier.HEAVY;
        activeTierProperty.set(ModelTier.HEAVY);
        updateBadge();
        scheduleHeavyUnload();
    }

    private void loadAndWarm(Path path, ModelTier tier) throws Exception {
        String name = path.getFileName().toString();
        var profile = ModelLoadProfile.forTier(tier, path);
        if (backend.status() == InferenceService.Status.READY
                && name.equals(backend.modelName())
                && backend.activeProfile().equals(profile)) {
            return;
        }
        backend.loadModel(path, tier);
        backend.preparePromptCache(inferenceConfig);
        backend.warmup(inferenceConfig);
    }

    /** Frees Metal/unified memory from the light model before loading a ~19 GB heavy GGUF. */
    private void releaseMemoryBeforeHeavyLoad() {
        if (activeTier == ModelTier.HEAVY || backend.status() != InferenceService.Status.READY) {
            return;
        }
        LOG.info("Releasing loaded model before heavy load (free RAM: "
                + String.format("%.1f GB", SystemMemory.freeBytes() / 1e9) + ")");
        backend.unloadModel();
        System.gc();
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void switchToLightAfterHeavyUse() {
        Thread.startVirtualThread(() -> {
            try {
                ensureLightModel();
            } catch (Exception e) {
                LOG.warning("Could not switch back to light model: " + e.getMessage());
            }
        });
    }

    private void scheduleHeavyUnload() {
        cancelHeavyUnload();
        heavyUnloadTask = scheduler.schedule(
                () -> {
                    if (activeTier == ModelTier.HEAVY && userOverride != RoutingMode.HEAVY_ONLY) {
                        LOG.info("Heavy model idle timeout — unloading and restoring light model");
                        switchToLightAfterHeavyUse();
                    }
                },
                HEAVY_IDLE_UNLOAD_MINUTES,
                TimeUnit.MINUTES);
    }

    private void cancelHeavyUnload() {
        if (heavyUnloadTask != null) {
            heavyUnloadTask.cancel(false);
            heavyUnloadTask = null;
        }
    }

    public boolean keywordHeuristicTriggersHeavy(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        if (prompt.length() > 1200) {
            return true;
        }
        if (prompt.contains("```")) {
            return true;
        }
        return HEAVY_KEYWORDS.matcher(prompt).find();
    }

    boolean runLightModelClassification(String prompt, String contextSummary) {
        try {
            ensureLightModel();
            String classificationPrompt =
                    """
                    Classify the user request. Reply with exactly one word: HEAVY or LIGHT.
                    HEAVY = complex multi-step reasoning, code generation, debugging, architecture, tool/MCP sequences.
                    LIGHT = chat, simple Q&A, greetings, short factual answers.

                    Context: %s
                    Request: %s
                    Answer:"""
                            .formatted(safeSummary(contextSummary), truncate(prompt, 600));
            String answer = backend.quickClassify(classificationPrompt, 6);
            boolean heavy = answer.contains("HEAVY");
            LOG.fine("Light classifier: " + answer + " → " + (heavy ? "HEAVY" : "LIGHT"));
            return heavy;
        } catch (Exception e) {
            LOG.warning("Classification failed, defaulting to light: " + e.getMessage());
            return false;
        }
    }

    private static String safeSummary(String contextSummary) {
        if (contextSummary == null || contextSummary.isBlank()) {
            return "(none)";
        }
        return truncate(contextSummary, 200);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public void setUserOverride(RoutingMode mode) {
        userOverride = mode != null ? mode : RoutingMode.AUTO;
        appSettings.setRoutingMode(userOverride);
        routingModeProperty.set(userOverride);
        updateBadge();
        if (userOverride == RoutingMode.LIGHT_ONLY && activeTier == ModelTier.HEAVY) {
            switchToLightAfterHeavyUse();
        }
    }

    public RoutingMode getUserOverride() {
        return userOverride;
    }

    public RoutingMode getCurrentRoutingMode() {
        return userOverride;
    }

    /** Forces the next message to use the given mode, then reverts to session override. */
    public void setSingleTurnOverride(RoutingMode mode) {
        singleTurnOverride = mode;
    }

    public void toggleAutoHeavy() {
        if (userOverride == RoutingMode.HEAVY_ONLY) {
            setUserOverride(RoutingMode.AUTO);
        } else {
            setUserOverride(RoutingMode.HEAVY_ONLY);
        }
    }

    public ModelTier getActiveTier() {
        return activeTier;
    }

    public boolean wasLastEscalationAuto() {
        return lastEscalationAuto;
    }

    public String resolveLightFilename() {
        String configured = appSettings.getLightModel();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return modelManager.defaultModelFilename();
    }

    public String resolveHeavyFilename() {
        String configured = appSettings.getHeavyModel();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return modelManager.heavyModelFilename();
    }

    public static String buildContextSummary(List<tech.rawden.ara.model.ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        int start = Math.max(0, history.size() - 4);
        var sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            var msg = history.get(i);
            sb.append(msg.role().name().toLowerCase(Locale.ROOT))
                    .append(": ")
                    .append(truncate(msg.content(), 120))
                    .append("; ");
        }
        return sb.toString().trim();
    }

    private void updateBadge() {
        ModelTier tier = activeTier != null ? activeTier : ModelTier.LIGHT;
        badgeLabel.set(tier.badgeLabel());
        String detail = switch (userOverride) {
            case HEAVY_ONLY -> "User-forced heavy for this session";
            case LIGHT_ONLY -> "User-forced light for this session";
            case AUTO -> tier == ModelTier.HEAVY
                    ? (lastEscalationAuto ? "Auto-selected for complex reasoning / tool calling" : "Heavy model active")
                    : "Auto routing — light model";
        };
        badgeDetail.set(detail);
    }

    public StringProperty badgeLabelProperty() {
        return badgeLabel;
    }

    public StringProperty badgeDetailProperty() {
        return badgeDetail;
    }

    public BooleanProperty escalatedThisTurnProperty() {
        return escalatedThisTurn;
    }

    public ObjectProperty<RoutingMode> routingModeProperty() {
        return routingModeProperty;
    }

    public ObjectProperty<ModelTier> activeTierProperty() {
        return activeTierProperty;
    }

    public void shutdown() {
        cancelHeavyUnload();
        scheduler.shutdownNow();
    }
}