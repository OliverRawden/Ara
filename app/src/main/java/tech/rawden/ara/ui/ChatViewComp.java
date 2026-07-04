package tech.rawden.ara.ui;

import tech.rawden.ara.ai.InferenceService;
import tech.rawden.ara.ai.ModelPreloader;
import tech.rawden.ara.ai.ModelRouter;
import tech.rawden.ara.ai.ModelTier;
import tech.rawden.ara.ai.RoutingInferenceService;
import tech.rawden.ara.ai.RoutingMode;
import tech.rawden.ara.comp.RegionBuilder;
import tech.rawden.ara.core.AppLog;
import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.core.SecurityService;
import tech.rawden.ara.integration.TeamOrchestrator;
import tech.rawden.ara.integration.VexProtocolCatalog;
import tech.rawden.ara.model.MemoryGraphService;
import tech.rawden.ara.model.AuditLogEntry;
import tech.rawden.ara.model.AuditLogStorage;
import tech.rawden.ara.model.ChatMessage;
import tech.rawden.ara.model.AppSettings;
import tech.rawden.ara.model.ChatSession;
import tech.rawden.ara.model.InferenceConfig;
import tech.rawden.ara.model.SettingsStorage;
import tech.rawden.ara.tool.CustomToolExecutor;
import tech.rawden.ara.tool.TerminalExecutor;
import tech.rawden.ara.tool.ToolCall;
import tech.rawden.ara.tool.ToolCallDisplay;
import tech.rawden.ara.tool.WebSearchService;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Active chat session: message bubbles, streaming inference, and the tool agent loop (max 5 rounds).
 * Model loading is handled by {@link ModelPreloader} — send waits for an in-flight preload rather
 * than starting a fresh 30s load on first message.
 */
public class ChatViewComp extends RegionBuilder<VBox> {

    private static final Logger LOG = AppLog.of("chat");

    private final ChatSession session;
    private final InferenceService inferenceService;
    private final ModelRouter modelRouter;
    private final ModelPreloader modelPreloader;
    private final InferenceConfig config;
    private final SettingsStorage settingsStorage;
    private final AppSettings appSettings;
    private final Runnable onSessionUpdated;

    private VBox messageContainer;
    private TextField inputField;
    private ModelStatusControl modelStatusControl;
    private HBox escalationBanner;
    private Button sendButton;
    private FontIcon sendButtonIcon;
    private boolean generating;

    private final Set<String> executedToolCalls = ConcurrentHashMap.newKeySet();
    private final AuditLogStorage auditLogStorage = new AuditLogStorage();
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final long STREAM_UI_INTERVAL_MS = 50;

    private String pendingTitleSource;

    private static final double MAX_BUBBLE_RATIO = 0.82;
    private static final double MAX_BUBBLE_ABSOLUTE = 800;

    public ChatViewComp(
            ChatSession session,
            InferenceService inferenceService,
            ModelRouter modelRouter,
            ModelPreloader modelPreloader,
            InferenceConfig config,
            SettingsStorage settingsStorage,
            AppSettings appSettings,
            Runnable onSessionUpdated) {
        this.session = session;
        this.inferenceService = inferenceService;
        this.modelRouter = modelRouter;
        this.modelPreloader = modelPreloader;
        this.config = config;
        this.settingsStorage = settingsStorage;
        this.appSettings = appSettings;
        this.onSessionUpdated = onSessionUpdated;
    }

    @Override
    protected VBox createSimple() {
        var root = new VBox();
        root.getStyleClass().add("ara-chat-view");

        // Messages area
        var messagesBox = createMessagesArea();
        VBox.setVgrow(messagesBox, Priority.ALWAYS);

        // Input area
        var inputArea = createInputArea();

        root.getChildren().addAll(messagesBox, inputArea);
        return root;
    }

    private Region createMessagesArea() {
        messageContainer = new VBox(12);
        messageContainer.setPadding(new Insets(20, 24, 20, 24));
        messageContainer.getStyleClass().add("ara-message-container");

        // Populate existing messages
        rebuildMessages();

        var scroll = new ScrollPane(messageContainer);
        scroll.setFitToWidth(true);
        scroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        scroll.hvalueProperty().addListener((obs, old, value) -> {
            if (value.doubleValue() != 0) {
                scroll.setHvalue(0);
            }
        });
        scroll.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY())) {
                event.consume();
            }
        });
        scroll.viewportBoundsProperty()
                .addListener((obs, old, bounds) -> messageContainer.setPrefWidth(bounds.getWidth()));
        scroll.getStyleClass().add("ara-message-scroll");

        // Auto-scroll to bottom
        messageContainer.heightProperty().addListener((obs, old, val) -> {
            scroll.setVvalue(1.0);
        });

        return scroll;
    }

    private javafx.beans.binding.DoubleBinding bubbleMaxWidth() {
        return Bindings.createDoubleBinding(
                () -> Math.min(messageContainer.getWidth() * MAX_BUBBLE_RATIO, MAX_BUBBLE_ABSOLUTE),
                messageContainer.widthProperty());
    }

    public void rebuildMessages() {
        if (messageContainer == null) return;
        messageContainer.getChildren().clear();
        for (var msg : session.messages()) {
            messageContainer.getChildren().add(createMessageBubble(msg));
        }
    }

    private void addMessageWithAnimation(javafx.scene.Node bubble) {
        bubble.setOpacity(0);
        bubble.setScaleX(0.92);
        bubble.setScaleY(0.92);
        messageContainer.getChildren().add(bubble);
        var fade = new FadeTransition(Duration.millis(180), bubble);
        fade.setFromValue(0);
        fade.setToValue(1);
        var scale = new ScaleTransition(Duration.millis(180), bubble);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1.0);
        scale.setToY(1.0);
        new ParallelTransition(fade, scale).play();
    }

    private Region createMessageBubble(ChatMessage msg) {
        if (msg.role() == ChatMessage.Role.TOOL) {
            return createToolCallBubble(msg);
        }

        var isUser = msg.role() == ChatMessage.Role.USER;

        Region contentNode;
        if (isUser) {
            var content = new Text(msg.content());
            content.setFont(Font.font("Inter", 14));
            var textFlow = new TextFlow(content);
            textFlow.setPadding(new Insets(10, 14, 10, 14));
            textFlow.getStyleClass().add("ara-message-user");
            textFlow.maxWidthProperty().bind(bubbleMaxWidth());
            content.wrappingWidthProperty().bind(bubbleMaxWidth());
            contentNode = textFlow;
        } else {
            contentNode = buildFormattedContent(ToolCallDisplay.forDisplay(msg.content()));
        }

        var copyBtn = createCopyButton(ToolCallDisplay.forDisplay(msg.content()));
        copyBtn.opacityProperty()
                .bind(Bindings.when(copyBtn.hoverProperty())
                        .then(1.0)
                        .otherwise(Bindings.when(contentNode.hoverProperty())
                                .then(0.35)
                                .otherwise(0.0)));

        var stack = new StackPane(contentNode, copyBtn);
        StackPane.setAlignment(copyBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(copyBtn, new Insets(6, 6, 0, 0));

        var wrapper = new HBox(stack);
        wrapper.setPadding(new Insets(2, 0, 2, 0));
        wrapper.setAlignment(isUser ? Pos.TOP_RIGHT : Pos.TOP_LEFT);

        return wrapper;
    }

    private Region createToolCallBubble(ChatMessage msg) {
        var isExecuted = executedToolCalls.contains(msg.id());
        var content = msg.content();

        if (content.startsWith("Current date and time:")) {
            var body = new VBox(8);
            body.setPadding(new Insets(12));
            var header = new Text("Date & Time");
            header.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 12));
            header.getStyleClass().add("ara-tool-header");
            var icon = new FontIcon("mdi2c-calendar-clock");
            icon.setIconSize(14);
            var headerRow = new HBox(6, icon, header);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            var resultText = buildFormattedContent(content);
            resultText.getStyleClass().remove("ara-message-assistant");
            resultText.setStyle("-fx-background-color: transparent; -fx-background-radius: 0;");
            var resultsBox = new VBox(resultText);
            resultsBox.setPadding(new Insets(4, 0, 0, 24));

            body.getChildren().addAll(headerRow, resultsBox);
            body.getStyleClass().add("ara-tool-bubble");
            body.maxWidthProperty().bind(bubbleMaxWidth());
            var wrapper = new HBox(body);
            wrapper.setPadding(new Insets(2, 0, 2, 0));
            wrapper.setAlignment(Pos.TOP_LEFT);
            return wrapper;
        }

        if (content.startsWith("web_search:")) {
            var body = new VBox(8);
            body.setPadding(new Insets(12));
            var header = new Text("  Web Search");
            header.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 12));
            header.getStyleClass().add("ara-tool-header");
            var icon = new FontIcon("mdi2w-web");
            icon.setIconSize(14);
            var headerRow = new HBox(6, icon, header);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            if (!isExecuted) {
                var spinner = new Text("Searching...");
                spinner.setFont(Font.font("Inter", FontPosture.ITALIC, 12));
                spinner.setStyle("-fx-fill: -color-fg-subtle;");
                var spinnerBox = new HBox(spinner);
                spinnerBox.setPadding(new Insets(4, 0, 0, 24));
                body.getChildren().addAll(headerRow, spinnerBox);
            } else {
                var sepIdx = content.indexOf("\n\n");
                var resultsText = sepIdx >= 0 ? content.substring(sepIdx + 2) : "";
                var resultNode = buildFormattedContent(resultsText);
                resultNode.getStyleClass().remove("ara-message-assistant");
                resultNode.setStyle("-fx-background-color: transparent; -fx-background-radius: 0;");
                var resultsBox = new VBox(resultNode);
                resultsBox.setPadding(new Insets(4, 0, 0, 20));
                body.getChildren().addAll(headerRow, resultsBox);
            }

            body.getStyleClass().add("ara-tool-bubble");
            body.maxWidthProperty().bind(bubbleMaxWidth());
            var wrapper = new HBox(body);
            wrapper.setPadding(new Insets(2, 0, 2, 0));
            wrapper.setAlignment(Pos.TOP_LEFT);
            return wrapper;
        }

        String command;
        String output;
        if (isExecuted) {
            var parts = content.split("\n\n", 2);
            command = parts[0];
            output = parts.length > 1 ? parts[1] : "";
        } else {
            command = content;
            output = null;
        }

        var header = new Text("  Terminal");
        header.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 12));
        header.getStyleClass().add("ara-tool-header");

        var icon = new FontIcon("mdi2c-console");
        icon.setIconSize(14);

        var headerRow = new HBox(6, icon, header);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        var cmdText = new Text("$ " + command);
        cmdText.setFont(Font.font("Menlo", 12));
        cmdText.setStyle("-fx-fill: -color-fg-default;");
        cmdText.wrappingWidthProperty().bind(bubbleMaxWidth().subtract(24));

        var body = new VBox(8, headerRow, cmdText);
        body.setPadding(new Insets(12));

        if (!isExecuted) {
            var runBtn = new Button(" Run", new FontIcon("mdi2p-play"));
            runBtn.getStyleClass().add("ara-tool-run-btn");
            runBtn.setOnAction(e -> executeToolCall(msg, 0));
            body.getChildren().add(runBtn);
        }

        if (output != null && !output.isEmpty()) {
            var sep = new Text("────────────────");
            sep.setStyle("-fx-fill: -color-border-default;");
            sep.setFont(Font.font("Menlo", 10));
            sep.setWrappingWidth(1);

            var outText = new Text(output);
            outText.setFont(Font.font("Menlo", 11));
            outText.setStyle("-fx-fill: -color-fg-subtle;");
            outText.wrappingWidthProperty().bind(bubbleMaxWidth().subtract(24));

            body.getChildren().addAll(sep, outText);
        }

        body.getStyleClass().add("ara-tool-bubble");
        body.maxWidthProperty().bind(bubbleMaxWidth());

        var wrapper = new HBox(body);
        wrapper.setPadding(new Insets(2, 0, 2, 0));
        wrapper.setAlignment(Pos.TOP_LEFT);
        return wrapper;
    }

    private VBox buildFormattedContent(String content) {
        return MarkdownRenderer.render(content, bubbleMaxWidth());
    }

    private Button createCopyButton(String text) {
        var copyIcon = new FontIcon("mdi2c-content-copy");
        copyIcon.setIconSize(12);
        var btn = new Button("", copyIcon);
        btn.getStyleClass().add("ara-copy-btn");
        btn.setOnAction(e -> {
            var cc = new ClipboardContent();
            cc.putString(text);
            Clipboard.getSystemClipboard().setContent(cc);
            copyIcon.setIconLiteral("mdi2c-check");
            var revert = new javafx.animation.PauseTransition(Duration.millis(1400));
            revert.setOnFinished(ev -> copyIcon.setIconLiteral("mdi2c-content-copy"));
            revert.playFromStart();
        });
        return btn;
    }

    private Region createInputArea() {
        inputField = new TextField();
        inputField.setPromptText("Message Ara…  (/light, /heavy, /team)");
        inputField.getStyleClass().add("ara-input-field");
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                sendMessage();
            }
        });

        sendButton = new Button();
        sendButtonIcon = new FontIcon("mdi2s-send");
        sendButtonIcon.setIconSize(18);
        sendButton.setGraphic(sendButtonIcon);
        sendButton.getStyleClass().add("ara-send-btn");
        sendButton.setOnAction(e -> {
            if (generating) {
                stopGeneration();
            } else {
                sendMessage();
            }
        });

        if (modelRouter != null) {
            modelStatusControl = new ModelStatusControl(modelRouter);
            modelStatusControl.setOnRoutingChanged(() -> {
                appSettings.setRoutingMode(modelRouter.getCurrentRoutingMode());
                settingsStorage.save(appSettings);
            });
        }

        var inputRow = new HBox(6);
        inputRow.setAlignment(Pos.CENTER);
        if (modelStatusControl != null) {
            inputRow.getChildren().add(modelStatusControl);
        }
        inputRow.getChildren().addAll(inputField, sendButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        var wrapper = new VBox();
        wrapper.getStyleClass().add("ara-input-bar");
        wrapper.setPadding(new Insets(8, 20, 14, 20));
        wrapper.getChildren().add(inputRow);

        return wrapper;
    }

    private void sendMessage() {
        var text = inputField.getText();
        if (text == null || text.isBlank()) return;

        var trimmed = text.trim();
        if (handleSlashCommand(trimmed)) {
            inputField.clear();
            return;
        }

        inputField.clear();
        setGenerating(true);

        // Always show the user bubble immediately for instant feedback.
        // Model load (if needed) happens in background; response comes after.
        var userMsg = ChatMessage.userMessage(session.id(), text);
        session.addMessage(userMsg);
        addMessageWithAnimation(createMessageBubble(userMsg));

        boolean isFirstMessage =
                "New Chat".equals(session.title()) || session.title().isBlank();
        if (isFirstMessage) {
            pendingTitleSource = text;
            session.setTitle("...");
            onSessionUpdated.run();
        }

        LOG.info("User message sent (chars=" + text.length() + ", session=" + session.id() + ")");

        Runnable startInference = () -> generateResponse(text, 0);

        if (inferenceService.status() != InferenceService.Status.READY) {
            LOG.info("Waiting for model load before generating response (status=" + inferenceService.status() + ")");
        }
        modelPreloader.whenInferenceReady(
                () -> Platform.runLater(startInference),
                error -> Platform.runLater(() -> {
                    LOG.warning("Model not ready: " + error.getMessage());
                    setGenerating(false);
                }));
    }

    private void setGenerating(boolean active) {
        generating = active;
        sendButton.setDisable(false);
        sendButtonIcon.setIconLiteral(active ? "mdi2s-stop" : "mdi2s-send");
        if (active) {
            sendButton.getStyleClass().add("ara-stop-btn");
        } else {
            sendButton.getStyleClass().remove("ara-stop-btn");
        }
    }

    private void stopGeneration() {
        inferenceService.cancelGeneration();
        audit("GENERATION_STOPPED", "User stopped inference (protocol 10 kill analogue)", 1);
    }

    private void updateStreamingBubble(Region bubble, VBox contentBox, String displayText) {
        if (contentBox == null) {
            return;
        }
        if (displayText.isEmpty()) {
            contentBox.getChildren().clear();
            return;
        }
        var formatted = buildFormattedContent(displayText);
        contentBox.getChildren().setAll(formatted.getChildren());
        var copyBtn = findCopyButton(bubble);
        if (copyBtn != null) {
            copyBtn.setOnAction(e -> {
                var cc = new ClipboardContent();
                cc.putString(displayText);
                Clipboard.getSystemClipboard().setContent(cc);
            });
        }
    }

    /** Runs after the first assistant reply finishes — never blocks the main inference queue. */
    private void maybeGenerateTitle() {
        if (pendingTitleSource == null) {
            return;
        }
        var source = pendingTitleSource;
        pendingTitleSource = null;
        inferenceService.generateTitle(
                source,
                title -> Platform.runLater(() -> {
                    session.setTitle(title);
                    onSessionUpdated.run();
                }));
    }

    private boolean handleSlashCommand(String text) {
        if (modelRouter == null || !text.startsWith("/")) {
            return false;
        }
        var lower = text.toLowerCase();
        if (lower.equals("/power") || lower.equals("/heavy")) {
            modelRouter.setSingleTurnOverride(RoutingMode.HEAVY_ONLY);
            showSystemNote("Heavy model will be used for your next message.");
            return true;
        }
        if (lower.equals("/light")) {
            modelRouter.setSingleTurnOverride(RoutingMode.LIGHT_ONLY);
            showSystemNote("Light model will be used for your next message.");
            return true;
        }
        if (lower.equals("/model")) {
            var mode = modelRouter.getCurrentRoutingMode();
            var tier = modelRouter.getActiveTier();
            showSystemNote(String.format(
                    "Routing: %s · Active: %s · %s",
                    mode, tier.badgeLabel(), modelRouter.badgeDetailProperty().get()));
            return true;
        }
        if (lower.startsWith("/team")) {
            return handleTeamCommand(text);
        }
        if (lower.equals("/team-off") || lower.equals("/team off")) {
            session.setActiveTeamId(null);
            session.setTeamHandoffContext("");
            onSessionUpdated.run();
            showSystemNote("Agent team deactivated for this chat.");
            return true;
        }
        return false;
    }

    private boolean handleTeamCommand(String text) {
        var parts = text.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            var teams = VexProtocolCatalog.teams();
            if (teams.isEmpty()) {
                showSystemNote("No team protocols found. Seed protocol 20 in Vex (~/Documents/Vex/Protocols/).");
            } else {
                var listing = new StringBuilder("Available teams: ");
                for (int i = 0; i < teams.size(); i++) {
                    if (i > 0) {
                        listing.append(", ");
                    }
                    listing.append(teams.get(i).id()).append('=').append(teams.get(i).name());
                }
                listing.append(". Usage: /team <id>");
                showSystemNote(listing.toString());
            }
            return true;
        }
        try {
            int teamId = Integer.parseInt(parts[1].strip());
            var team = VexProtocolCatalog.findById(teamId).filter(TeamOrchestrator::isTeam);
            if (team.isEmpty()) {
                showSystemNote("Protocol " + teamId + " is not a team. Check Vex Protocols for type: team.");
                return true;
            }
            session.setActiveTeamId(teamId);
            session.setTeamHandoffContext("");
            onSessionUpdated.run();
            var members = TeamOrchestrator.parseMembers(team.get());
            var note = new StringBuilder("Team activated: ").append(team.get().name()).append(" (").append(teamId).append(").");
            if (!members.isEmpty()) {
                note.append(" Members: ");
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) {
                        note.append(", ");
                    }
                    note.append('[').append(members.get(i).role()).append(']');
                }
                note.append(". Prefix replies with [role] for tier routing.");
            }
            showSystemNote(note.toString());
            return true;
        } catch (NumberFormatException e) {
            showSystemNote("Usage: /team <protocol-id>");
            return true;
        }
    }

    private void showSystemNote(String note) {
        var msg = ChatMessage.assistantMessage(session.id(), note);
        session.addMessage(msg);
        addMessageWithAnimation(createMessageBubble(msg));
        onSessionUpdated.run();
        scrollToBottom();
    }

    private void showEscalationBanner() {
        removeEscalationBanner();
        var label = new Text("Heavy model engaged for this complex task. ");
        label.setFont(Font.font("Inter", 11));
        label.setStyle("-fx-fill: -color-fg-subtle;");

        var switchLink = new Text("Switch to Light");
        switchLink.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 11));
        switchLink.setStyle("-fx-fill: -color-accent-fg; -fx-underline: true; -fx-cursor: hand;");
        switchLink.setOnMouseClicked(e -> {
            modelRouter.setUserOverride(RoutingMode.LIGHT_ONLY);
            appSettings.setRoutingMode(RoutingMode.LIGHT_ONLY);
            settingsStorage.save(appSettings);
            removeEscalationBanner();
        });

        var flow = new TextFlow(label, switchLink);
        flow.setPadding(new Insets(6, 14, 6, 14));
        flow.getStyleClass().add("ara-escalation-banner");
        flow.maxWidthProperty().bind(bubbleMaxWidth());

        escalationBanner = new HBox(flow);
        escalationBanner.setAlignment(Pos.CENTER_LEFT);
        escalationBanner.setPadding(new Insets(0, 24, 0, 24));
        messageContainer.getChildren().add(escalationBanner);
        scrollToBottom();
    }

    private void removeEscalationBanner() {
        if (escalationBanner != null && messageContainer.getChildren().contains(escalationBanner)) {
            messageContainer.getChildren().remove(escalationBanner);
        }
        escalationBanner = null;
    }

    private void generateResponse(String userMessage, int toolRound) {
        LOG.fine("generateResponse: toolRound=" + toolRound + ", model=" + inferenceService.modelName());
        if (toolRound >= MAX_TOOL_ROUNDS) {
            LOG.warning("Tool round limit reached (" + MAX_TOOL_ROUNDS + ") — stopping agent loop");
            setGenerating(false);
            maybeGenerateTitle();
            return;
        }

        var sb = new StringBuilder();
        var placeholder = ChatMessage.assistantMessage(session.id(), "");
        session.addMessage(placeholder);
        var bubble = createMessageBubble(placeholder);
        addMessageWithAnimation(bubble);
        scrollToBottom();

        var allMsgs = session.messages();
        var history = allMsgs.subList(0, allMsgs.size() - 1);
        var contentBox = findMessageContent(bubble);
        var lastStreamUiMs = new long[] {0};

        Runnable startInference = () -> runInferenceJob(userMessage, history, sb, bubble, contentBox, lastStreamUiMs, toolRound);

        if (modelRouter != null) {
            Thread.startVirtualThread(() -> {
                try {
                    var decision = modelRouter.prepareForRequest(
                            userMessage, ModelRouter.buildContextSummary(history));
                    LOG.info("Chat routing: tier=" + decision.tier() + ", escalated=" + decision.autoEscalated()
                            + ", reason=" + decision.reason());
                    Platform.runLater(() -> {
                        if (decision.autoEscalated() && decision.tier() == ModelTier.HEAVY) {
                            showEscalationBanner();
                        } else if (!userMessage.isBlank()) {
                            removeEscalationBanner();
                        }
                        startInference.run();
                    });
                } catch (Exception e) {
                    LOG.warning("Model routing failed: " + e.getMessage());
                    Platform.runLater(() -> {
                        setGenerating(false);
                        showSystemNote("Model routing failed: " + e.getMessage());
                    });
                }
            });
            return;
        }

        startInference.run();
    }

    private void runInferenceJob(
            String userMessage,
            List<ChatMessage> history,
            StringBuilder sb,
            Region bubble,
            VBox contentBox,
            long[] lastStreamUiMs,
            int toolRound) {
        var routedMessage = wrapMessageForActiveTeam(userMessage);
        var inference = resolveInferenceBackend();
        inference.generateWithTools(
                routedMessage,
                config,
                history,
                token -> {
                    sb.append(token);
                    long now = System.currentTimeMillis();
                    if (now - lastStreamUiMs[0] < STREAM_UI_INTERVAL_MS && !token.contains("\n")) {
                        return;
                    }
                    lastStreamUiMs[0] = now;
                    var displayText = ToolCallDisplay.forDisplay(sb.toString());
                    Platform.runLater(() -> updateStreamingBubble(bubble, contentBox, displayText));
                },
                () -> Platform.runLater(() -> {
                    var msgs = session.messages();
                    var last = msgs.get(msgs.size() - 1);
                    msgs.remove(last);
                    var display = ToolCallDisplay.forDisplay(sb.toString());
                    captureTeamHandoff(display);
                    var complete = ChatMessage.assistantMessage(session.id(), display);
                    session.addMessage(complete);
                    rebuildMessages();
                    setGenerating(false);
                    onSessionUpdated.run();
                    maybeGenerateTitle();
                }),
                error -> Platform.runLater(() -> {
                    setGenerating(false);
                    var msgs = session.messages();
                    var last = msgs.get(msgs.size() - 1);
                    msgs.remove(last);
                    String content;
                    if (error instanceof java.util.concurrent.CancellationException) {
                        var partial = ToolCallDisplay.forDisplay(sb.toString());
                        content = partial.isBlank() ? "[Stopped]" : partial + "\n\n[Stopped]";
                    } else {
                        content = "Error: " + error.getMessage();
                    }
                    var errMsg = ChatMessage.assistantMessage(session.id(), content);
                    session.addMessage(errMsg);
                    onSessionUpdated.run();
                    maybeGenerateTitle();
                }),
                toolCall -> Platform.runLater(() -> {
                    var msgs = session.messages();
                    var placeholder = msgs.get(msgs.size() - 1);
                    handleToolCall(toolCall, sb.toString(), placeholder, toolRound);
                }));
    }

    private InferenceService resolveInferenceBackend() {
        if (inferenceService instanceof RoutingInferenceService ris) {
            return ris.backend();
        }
        return inferenceService;
    }

    private void handleToolCall(ToolCall toolCall, String partialText, ChatMessage placeholder, int toolRound) {
        LOG.info("Tool call: " + toolCall.name() + " (round=" + toolRound + ")");
        if (!toolCall.isKnownTool()) {
            LOG.warning("Unknown tool requested: " + toolCall.name());
            audit("TOOL_CALL_DENIED", "Unknown tool: " + toolCall.name(), 2);
            var msgs = session.messages();
            msgs.remove(placeholder);
            var denied = ChatMessage.toolMessage(
                    session.id(), "Error: unknown tool '" + toolCall.name() + "'. Edit tools in Vex Protocols.");
            session.addMessage(denied);
            rebuildMessages();
            scrollToBottom();
            generateResponse("", toolRound + 1);
            return;
        }

        var msgs = session.messages();
        msgs.remove(placeholder);

        var visiblePartial = ToolCallDisplay.forDisplay(partialText);
        if (!visiblePartial.isEmpty()) {
            session.addMessage(ChatMessage.assistantMessage(session.id(), visiblePartial));
        }

        if ("get_current_datetime".equals(toolCall.name())) {
            var now = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm"));
            var result = ChatMessage.toolMessage(session.id(), "Current date and time: " + now);
            executedToolCalls.add(result.id());
            session.addMessage(result);
            audit("TOOL_CALL", "get_current_datetime", 0);
            rebuildMessages();
            scrollToBottom();
            setGenerating(true);
            generateResponse("", toolRound + 1);
            return;
        }

        if ("web_search".equals(toolCall.name())) {
            var query = toolCall.getQuery();
            LOG.info("web_search tool called with query: \"" + query + "\"");
            if (query == null || query.isBlank()) return;

            audit("TOOL_CALL", "web_search: " + query, 1);

            var searchMsg = ChatMessage.toolMessage(session.id(), "web_search: " + query);
            session.addMessage(searchMsg);
            rebuildMessages();
            scrollToBottom();
            setGenerating(true);

            Thread.startVirtualThread(() -> {
                var result = WebSearchService.search(query);
                Platform.runLater(() -> {
                    executedToolCalls.add(searchMsg.id());
                    var displayText = "web_search: " + query + "\n\n" + result;
                    var messages = session.messages();
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).id().equals(searchMsg.id())) {
                            messages.set(i, ChatMessage.toolMessage(session.id(), displayText));
                            break;
                        }
                    }
                    rebuildMessages();
                    scrollToBottom();
                    generateResponse("", toolRound + 1);
                });
            });
            return;
        }

        // === Secure memory tools (privacy-preserving, works with encrypted context) ===
        if ("read_memory".equals(toolCall.name())) {
            audit("CONTEXT_ACCESS", "read_memory (secure tool)", 1);
            String memory = loadSecureMemory();
            var resultMsg = ChatMessage.toolMessage(session.id(), "read_memory result:\n\n" + memory);
            executedToolCalls.add(resultMsg.id());
            session.addMessage(resultMsg);
            rebuildMessages();
            scrollToBottom();
            setGenerating(true);
            generateResponse("", toolRound + 1);
            return;
        }

        if ("write_memory".equals(toolCall.name())) {
            String content = toolCall.getMemoryContent();
            if (content == null) content = "";
            audit("CONTEXT_ACCESS", "write_memory (secure tool, " + content.length() + " chars)", 2);
            saveSecureMemory(content);
            var resultMsg = ChatMessage.toolMessage(
                    session.id(), "write_memory: success (" + content.length() + " bytes written to secure memory)");
            executedToolCalls.add(resultMsg.id());
            session.addMessage(resultMsg);
            rebuildMessages();
            scrollToBottom();
            setGenerating(true);
            generateResponse("", toolRound + 1);
            return;
        }

        if ("append_memory".equals(toolCall.name())) {
            try {
                var args = new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall.arguments());
                String title = args.has("title") ? args.get("title").asText() : "Note";
                String info = args.has("info") ? args.get("info").asText() : "";
                String entry = "\n\n### " + title + "\n\n**Date/Time:** "
                        + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        + "\n\n**Info:** "
                        + info + "\n";
                audit("CONTEXT_ACCESS", "append_memory: " + title, 1);
                String current = loadSecureMemory();
                saveSecureMemory(current + entry);
                var resultMsg = ChatMessage.toolMessage(
                        session.id(), "append_memory: entry \"" + title + "\" appended securely.");
                executedToolCalls.add(resultMsg.id());
                session.addMessage(resultMsg);
                rebuildMessages();
                scrollToBottom();
                setGenerating(true);
                generateResponse("", toolRound + 1);
            } catch (Exception e) {
                LOG.warning("Failed to parse append_memory: " + e);
            }
            return;
        }

        if (handleMemoryGraphTool(toolCall, toolRound)) {
            return;
        }

        var customPlan = CustomToolExecutor.plan(toolCall, config);
        if (customPlan.isPresent()) {
            handleCustomTool(customPlan.get(), toolCall, toolRound);
            return;
        }

        // === Traditional execute_command (now with permission + confirmation) ===
        var cmd = toolCall.getCommand();
        if (cmd == null) return;

        if (!config.terminalEnabled()) {
            audit("TOOL_CALL_DENIED", "execute_command blocked (terminal disabled in settings)", 2);
            var denied = ChatMessage.toolMessage(
                    session.id(),
                    "execute_command: " + cmd
                            + "\n\n[Terminal execution is currently disabled in Privacy & Security settings]");
            executedToolCalls.add(denied.id());
            session.addMessage(denied);
            rebuildMessages();
            scrollToBottom();
            return;
        }

        audit("TOOL_CALL", "execute_command (raw): " + (cmd.length() > 80 ? cmd.substring(0, 77) + "..." : cmd), 3);

        var toolMsg = ChatMessage.toolMessage(session.id(), cmd);
        session.addMessage(toolMsg);

        rebuildMessages();
        scrollToBottom();

        // Defer actual execution to button press in the bubble (existing UX)
        // The confirmation will happen in executeToolCall
    }

    private void handleCustomTool(CustomToolExecutor.Plan plan, ToolCall toolCall, int toolRound) {
        switch (plan) {
            case CustomToolExecutor.Plan.Immediate(var result) -> {
                audit("TOOL_CALL", "custom: " + toolCall.name(), 1);
                var resultMsg = ChatMessage.toolMessage(session.id(), toolCall.name() + " result:\n\n" + result);
                executedToolCalls.add(resultMsg.id());
                session.addMessage(resultMsg);
                rebuildMessages();
                scrollToBottom();
                setGenerating(true);
                generateResponse("", toolRound + 1);
            }
            case CustomToolExecutor.Plan.Terminal(var command) -> {
                audit("TOOL_CALL", "custom terminal: " + toolCall.name(), 2);
                var toolMsg = ChatMessage.toolMessage(session.id(), command);
                session.addMessage(toolMsg);
                rebuildMessages();
                scrollToBottom();
            }
            case CustomToolExecutor.Plan.AsyncWeb(var query) -> {
                audit("TOOL_CALL", "custom web_search: " + query, 1);
                var searchMsg = ChatMessage.toolMessage(session.id(), "web_search: " + query);
                session.addMessage(searchMsg);
                rebuildMessages();
                scrollToBottom();
                setGenerating(true);
                Thread.startVirtualThread(() -> {
                    var result = WebSearchService.search(query);
                    Platform.runLater(() -> {
                        executedToolCalls.add(searchMsg.id());
                        var displayText = CustomToolExecutor.formatWebResult(query, result);
                        var messages = session.messages();
                        for (int i = 0; i < messages.size(); i++) {
                            if (messages.get(i).id().equals(searchMsg.id())) {
                                messages.set(i, ChatMessage.toolMessage(session.id(), displayText));
                                break;
                            }
                        }
                        rebuildMessages();
                        scrollToBottom();
                        generateResponse("", toolRound + 1);
                    });
                });
            }
        }
    }

    private void executeToolCall(ChatMessage toolMsg, int toolRound) {
        var command = toolMsg.content();
        setGenerating(true);

        // === PRIVACY / SECURITY: Explicit confirmation for consequential terminal actions ===
        boolean needsConfirm = config.requireTerminalConfirmation();
        if (needsConfirm) {
            final boolean[] allowed = {false};
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            // Risk detection for better UX/polish
            String lowerCmd = command.toLowerCase();
            boolean highRisk = lowerCmd.contains("rm ")
                    || lowerCmd.contains("rm -")
                    || lowerCmd.contains("sudo")
                    || lowerCmd.contains("curl ") && lowerCmd.contains("|")
                    || lowerCmd.contains("wget ") && lowerCmd.contains("|")
                    || lowerCmd.contains("mkfs")
                    || lowerCmd.contains("dd if=")
                    || lowerCmd.contains("> /dev/");

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(highRisk ? "⚠ HIGH RISK Terminal Command" : "Confirm Terminal Command");
                alert.setHeaderText(
                        highRisk
                                ? "This command looks potentially destructive or privileged!"
                                : "Ara wants to run a terminal command on your computer");

                String warning = highRisk
                        ? "\n\nWARNING: This may delete files, require elevated access, or pipe untrusted code. Double-check carefully."
                        : "\n\nThis can read, write, or execute anything on your machine. Only allow commands you understand and trust.";

                alert.setContentText("Command:\n" + command + warning);

                TextArea text = new TextArea(command);
                text.setEditable(false);
                text.setWrapText(true);
                text.setPrefRowCount(5);
                alert.getDialogPane().setExpandableContent(text);

                ButtonType allow = new ButtonType("Allow (I understand)");
                ButtonType deny = new ButtonType("Deny", ButtonType.CANCEL.getButtonData());
                alert.getButtonTypes().setAll(allow, deny);

                if (highRisk) {
                    alert.getDialogPane().setStyle("-fx-border-color: -color-danger-emphasis; -fx-border-width: 2;");
                }

                alert.showAndWait().ifPresent(response -> {
                    allowed[0] = (response == allow);
                    latch.countDown();
                });
            });

            try {
                latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }

            if (!allowed[0]) {
                audit("TOOL_CALL_DENIED", "User denied terminal command: " + command, 3);
                Platform.runLater(() -> {
                    var msgs = session.messages();
                    for (int i = 0; i < msgs.size(); i++) {
                        if (msgs.get(i).id().equals(toolMsg.id())) {
                            msgs.set(i, ChatMessage.toolMessage(session.id(), command + "\n\n[User denied execution]"));
                            break;
                        }
                    }
                    rebuildMessages();
                    scrollToBottom();
                    generateResponse("", toolRound + 1);
                });
                setGenerating(false);
                return;
            }
        }

        Thread.startVirtualThread(() -> {
            var result = TerminalExecutor.execute(command);
            Platform.runLater(() -> {
                executedToolCalls.add(toolMsg.id());

                var output = new StringBuilder();
                if (!result.stdout().isEmpty()) {
                    output.append(result.stdout());
                }
                if (!result.stderr().isEmpty()) {
                    if (!output.isEmpty()) output.append("\n");
                    output.append(result.stderr());
                }
                if (result.exitCode() != 0) {
                    if (!output.isEmpty()) output.append("\n");
                    output.append("exit code: ").append(result.exitCode());
                }
                if (result.timedOut()) {
                    if (!output.isEmpty()) output.append("\n");
                    output.append("[command timed out]");
                }

                var displayText = command + "\n\n" + output;
                var msgs = session.messages();
                for (int i = 0; i < msgs.size(); i++) {
                    if (msgs.get(i).id().equals(toolMsg.id())) {
                        msgs.set(i, ChatMessage.toolMessage(session.id(), displayText));
                        break;
                    }
                }

                rebuildMessages();
                scrollToBottom();
                generateResponse("", toolRound + 1);
            });
        });
    }

    private String wrapMessageForActiveTeam(String userMessage) {
        return TeamOrchestrator.activeTeam(session)
                .map(team -> TeamOrchestrator.wrapUserMessageForTeam(userMessage, team, session))
                .orElse(userMessage);
    }

    private void captureTeamHandoff(String assistantText) {
        if (assistantText == null || assistantText.isBlank() || session.activeTeamId() == null) {
            return;
        }
        var matcher = java.util.regex.Pattern.compile("^\\s*\\[([a-zA-Z][a-zA-Z0-9_-]*)]\\s*", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(assistantText);
        if (matcher.find()) {
            TeamOrchestrator.appendHandoff(session, matcher.group(1), assistantText);
            onSessionUpdated.run();
        }
    }

    private boolean handleMemoryGraphTool(ToolCall toolCall, int toolRound) {
        if (!config.contextMemoryEnabled()) {
            if ("query_memory_graph".equals(toolCall.name())
                    || "upsert_memory_entity".equals(toolCall.name())
                    || "link_memory_entities".equals(toolCall.name())) {
                audit("TOOL_CALL_DENIED", toolCall.name() + " blocked (memory disabled)", 2);
                var denied = ChatMessage.toolMessage(
                        session.id(),
                        toolCall.name() + ": memory tools are disabled in Privacy & Security settings.");
                executedToolCalls.add(denied.id());
                session.addMessage(denied);
                rebuildMessages();
                scrollToBottom();
                setGenerating(true);
                generateResponse("", toolRound + 1);
                return true;
            }
            return false;
        }

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var args = mapper.readTree(toolCall.arguments());
            var graph = MemoryGraphService.get();
            String resultText;

            switch (toolCall.name()) {
                case "query_memory_graph" -> {
                    String query = args.has("query") ? args.get("query").asText() : "";
                    String kind = args.has("entity_kind") ? args.get("entity_kind").asText() : null;
                    int limit = args.has("limit") ? args.get("limit").asInt(20) : 20;
                    audit("CONTEXT_ACCESS", "query_memory_graph: " + (query.isBlank() ? "(list)" : query), 1);
                    resultText = graph.formatForAgent(graph.query(query, kind, limit));
                }
                case "upsert_memory_entity" -> {
                    String id = args.has("id") ? args.get("id").asText() : null;
                    String kind = args.has("kind") ? args.get("kind").asText() : "note";
                    String label = args.has("label") ? args.get("label").asText() : "";
                    String content = args.has("content") ? args.get("content").asText() : "";
                    audit("CONTEXT_ACCESS", "upsert_memory_entity: " + label, 1);
                    var entity = graph.upsertEntity(id, kind, label, content);
                    resultText = "upsert_memory_entity: saved [" + entity.id() + "] " + entity.kind() + " · "
                            + entity.label();
                }
                case "link_memory_entities" -> {
                    String fromId = args.has("from_id") ? args.get("from_id").asText() : "";
                    String toId = args.has("to_id") ? args.get("to_id").asText() : "";
                    String relationType = args.has("relation_type") ? args.get("relation_type").asText() : "related_to";
                    String note = args.has("note") ? args.get("note").asText() : null;
                    audit("CONTEXT_ACCESS", "link_memory_entities: " + fromId + " -> " + toId, 1);
                    var relation = graph.linkEntities(fromId, toId, relationType, note);
                    resultText = "link_memory_entities: " + relation.fromId() + " -[" + relation.relationType() + "]-> "
                            + relation.toId();
                }
                default -> {
                    return false;
                }
            }

            var resultMsg = ChatMessage.toolMessage(session.id(), toolCall.name() + " result:\n\n" + resultText);
            executedToolCalls.add(resultMsg.id());
            session.addMessage(resultMsg);
            rebuildMessages();
            scrollToBottom();
            setGenerating(true);
            generateResponse("", toolRound + 1);
            return true;
        } catch (Exception e) {
            LOG.warning("Memory graph tool failed: " + e.getMessage());
            var err = ChatMessage.toolMessage(session.id(), toolCall.name() + " error: " + e.getMessage());
            executedToolCalls.add(err.id());
            session.addMessage(err);
            rebuildMessages();
            scrollToBottom();
            setGenerating(true);
            generateResponse("", toolRound + 1);
            return true;
        }
    }

    // === Secure memory helpers (respect encryption + privacy model) ===

    private String loadSecureMemory() {
        try {
            var path = AraPaths.contextFile();
            if (!java.nio.file.Files.exists(path)) {
                return """
                        # Ara Persistent Memory

                        Vex protocols (~/Documents/Vex/Protocols/) are auto-loaded into Ara's system prompt.
                        Agent tools 101–109; use read_memory (104) or query_memory_graph (107) at session start.
                        Protocol 102 = get_current_datetime. Teams: /team 20.

                        ## Active Context

                        """;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            byte[] plain = SecurityService.decrypt(bytes);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.warning("Failed to load secure memory: " + e.getMessage());
            return "";
        }
    }

    private void saveSecureMemory(String content) {
        try {
            var path = AraPaths.contextFile();
            java.nio.file.Files.createDirectories(path.getParent());
            byte[] plain = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] toWrite = SecurityService.isEncryptionEnabled() ? SecurityService.encrypt(plain) : plain;
            java.nio.file.Files.write(path, toWrite);
            audit(
                    "CONTEXT_ACCESS",
                    "memory file updated (" + plain.length + " bytes, encrypted="
                            + SecurityService.isEncryptionEnabled() + ")",
                    1);
        } catch (Exception e) {
            LOG.warning("Failed to save secure memory: " + e.getMessage());
        }
    }

    private void audit(String type, String details, int riskLevel) {
        try {
            var entry = new AuditLogEntry(type, details, session != null ? session.id() : null, riskLevel);
            var log = auditLogStorage.load();
            log.addEntry(entry);
            auditLogStorage.save(log);
        } catch (Exception e) {
            LOG.fine("Audit log write failed: " + e.getMessage());
        }
    }

    private void scrollToBottom() {
        if (messageContainer == null) return;
        var parent = messageContainer.getParent();
        if (parent instanceof ScrollPane scroll) {
            scroll.setVvalue(1.0);
        }
    }

    private VBox findMessageContent(javafx.scene.Node node) {
        if (node instanceof VBox v && v.getStyleClass().contains("ara-message-assistant")) return v;
        if (node instanceof javafx.scene.Parent p) {
            for (var child : p.getChildrenUnmodifiable()) {
                var found = findMessageContent(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Button findCopyButton(javafx.scene.Node node) {
        if (node instanceof Button b && b.getStyleClass().contains("ara-copy-btn")) return b;
        if (node instanceof javafx.scene.Parent p) {
            for (var child : p.getChildrenUnmodifiable()) {
                var found = findCopyButton(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
