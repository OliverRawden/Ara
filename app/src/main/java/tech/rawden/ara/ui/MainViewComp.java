package tech.rawden.ara.ui;

import tech.rawden.ara.ai.InferenceService;
import tech.rawden.ara.ai.ModelManager;
import tech.rawden.ara.ai.ModelPreloader;
import tech.rawden.ara.comp.RegionBuilder;
import tech.rawden.ara.core.AraModel;
import tech.rawden.ara.core.AraTheme;
import tech.rawden.ara.model.AppSettings;
import tech.rawden.ara.model.ChatHistory;
import tech.rawden.ara.model.ChatSession;
import tech.rawden.ara.model.ChatStorage;
import tech.rawden.ara.model.InferenceConfig;
import tech.rawden.ara.model.SettingsStorage;

import javafx.animation.FadeTransition;
import javafx.scene.Cursor;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.logging.Logger;

/**
 * Root shell: sidebar + chat/settings content area. Builds chat view eagerly but defers
 * settings until first open. Model preload is owned by {@link ModelPreloader} (started from
 * {@link tech.rawden.ara.Main} after unlock).
 */
public class MainViewComp extends RegionBuilder<HBox> {

    private static final Logger LOG = Logger.getLogger(MainViewComp.class.getName());

    private final AraModel model;
    private ChatHistory chatHistory;
    private final InferenceService inferenceService;
    private final ModelManager modelManager;
    private final ModelPreloader modelPreloader;
    private final InferenceConfig config;
    private final ChatStorage chatStorage;
    private final SettingsStorage settingsStorage;
    private final AppSettings appSettings;

    private SidebarComp sidebar;
    private Region chatViewNode;
    private Region settingsViewNode;
    private String chatViewSessionId;
    private VBox contentArea;

    public MainViewComp(
            AraModel model,
            ChatHistory chatHistory,
            InferenceService inferenceService,
            ModelManager modelManager,
            ModelPreloader modelPreloader,
            InferenceConfig config,
            ChatStorage chatStorage,
            SettingsStorage settingsStorage,
            AppSettings appSettings) {
        this.model = model;
        this.chatHistory = chatHistory;
        this.inferenceService = inferenceService;
        this.modelManager = modelManager;
        this.modelPreloader = modelPreloader;
        this.config = config;
        this.chatStorage = chatStorage;
        this.settingsStorage = settingsStorage;
        this.appSettings = appSettings;
    }

    @Override
    protected HBox createSimple() {
        sidebar = new SidebarComp(model, chatHistory, this::onNewChat, this::onSelectSession, this::onDeleteSession);

        contentArea = new VBox();
        contentArea.getStyleClass().add("ara-content-area");
        HBox.setHgrow(contentArea, Priority.ALWAYS);
        contentArea.setMinWidth(
                0); // Prevent the content area from forcing the sidebar to shrink below the user's chosen width (e.g.
        // when switching to Settings, which has wider rigid content, on small windows)

        ensureChatView();
        // Do NOT rebuildSettingsView() at startup — it is heavy (model list thread, many controls, TextAreas, etc.).
        // Build lazily the first time the user switches to Settings. This significantly speeds initial window
        // appearance.
        // rebuildSettingsView();   <--- removed for performance

        showView(model.currentView());
        model.currentViewProperty().addListener((obs, old, view) -> showView(view));

        var sidebarNode = sidebar.build();
        makeSidebarDraggable(sidebarNode);
        var root = new HBox(sidebarNode, contentArea);
        root.getStyleClass().add("ara-root");
        return root;
    }

    private void showView(AraModel.View view) {
        contentArea.getChildren().clear();
        switch (view) {
            case CHAT -> {
                ensureChatView();
                if (chatViewNode != null) contentArea.getChildren().add(chatViewNode);
            }
            case SETTINGS -> {
                try {
                    rebuildSettingsView();
                    if (settingsViewNode != null) contentArea.getChildren().add(settingsViewNode);
                } catch (Exception e) {
                    LOG.severe("Failed to build settings view: " + e.getMessage());
                }
            }
        }
        // Subtle fade for smooth view switch (polish)
        contentArea.setOpacity(0);
        var fade = new FadeTransition(Duration.millis(120), contentArea);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void ensureChatView() {
        var session = chatHistory.activeSession();
        if (session == null) {
            if (chatViewNode != null && chatViewSessionId == null) {
                return;
            }
            chatViewSessionId = null;
            chatViewNode = createEmptyChatView();
            VBox.setVgrow(chatViewNode, Priority.ALWAYS);
            chatViewNode.setMinWidth(0);
            return;
        }
        var sid = session.id();
        if (sid.equals(chatViewSessionId) && chatViewNode != null) {
            return; // Already have the right view cached
        }
        chatViewSessionId = sid;
        chatViewNode = new ChatViewComp(session, inferenceService, modelPreloader, config, () -> {
                    sidebar.rebuildChatList();
                    chatStorage.save(chatHistory);
                })
                .style("ara-chat-wrapper")
                .build();
        VBox.setVgrow(chatViewNode, Priority.ALWAYS);
        chatViewNode.setMinWidth(0);
    }

    private Region createEmptyChatView() {
        var prompt = new Text("Create a new chat");
        prompt.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 18));
        prompt.getStyleClass().add("ara-empty-chat-text");

        var emptyView = new StackPane(prompt);
        emptyView.getStyleClass().add("ara-empty-chat-view");
        return emptyView;
    }

    private void rebuildSettingsView() {
        settingsViewNode = new SettingsViewComp(
                        model,
                        modelManager,
                        inferenceService,
                        config,
                        () -> model.selectView(AraModel.View.CHAT),
                        this::resetSettings,
                        this::clearChats,
                        settingsStorage,
                        appSettings)
                .style("ara-settings-wrapper")
                .build();
        VBox.setVgrow(settingsViewNode, Priority.ALWAYS);
        settingsViewNode.setMinWidth(0);
    }

    public void newChat() {
        onNewChat();
    }

    public void closeChat() {
        var session = chatHistory.activeSession();
        if (session != null) {
            chatHistory.removeSession(session);
            saveAll();
            chatViewSessionId = null;
            chatViewNode = null;
            sidebar.rebuildChatList();
            ensureChatView();
            if (model.currentView() == AraModel.View.CHAT) {
                showView(AraModel.View.CHAT);
            }
        }
    }

    /**
     * Update the chat history after async load (e.g. after encryption unlock).
     * Rebuilds relevant UI parts. Must be called on FX thread.
     */
    public void updateChatHistory(ChatHistory newHistory) {
        this.chatHistory = newHistory;
        this.chatViewSessionId = null;
        this.chatViewNode = null;
        if (sidebar != null) {
            sidebar.updateChatHistory(newHistory);
        }
        ensureChatView();
        if (model.currentView() == AraModel.View.CHAT) {
            showView(AraModel.View.CHAT);
        }
    }

    private void onNewChat() {
        chatHistory.createSession("New Chat");
        saveAll();
        chatViewSessionId = null; // Force rebuild on next show
        sidebar.rebuildChatList();
        ensureChatView();
        showChatView();
    }

    private void onSelectSession(ChatSession session) {
        chatHistory.setActiveSession(session);
        saveAll();
        chatViewSessionId = null; // Force rebuild since session changed
        sidebar.rebuildChatList();
        ensureChatView();
        showChatView();
    }

    private void showChatView() {
        if (model.currentView() == AraModel.View.CHAT) {
            showView(AraModel.View.CHAT);
            return;
        }
        model.selectView(AraModel.View.CHAT);
    }

    private void onDeleteSession(ChatSession session) {
        chatHistory.removeSession(session);
        saveAll();
        chatViewSessionId = null; // Force rebuild
        chatViewNode = null;
        sidebar.rebuildChatList();
        ensureChatView();
        if (model.currentView() == AraModel.View.CHAT) {
            showView(AraModel.View.CHAT);
        }
    }

    private void resetSettings() {
        settingsStorage.delete();
        applySettings(settingsStorage.load());
        rebuildSettingsView();
        showView(AraModel.View.SETTINGS);
    }

    private void clearChats() {
        chatStorage.delete();
        var reloadedHistory = chatStorage.load();
        chatHistory.replaceWith(reloadedHistory);
        chatViewSessionId = null;
        chatViewNode = null;
        sidebar.rebuildChatList();
        ensureChatView();
        rebuildSettingsView();
        showView(AraModel.View.SETTINGS);
    }

    private void saveAll() {
        chatStorage.save(chatHistory);
        appSettings.setDarkMode(AraTheme.isDark());
        appSettings.setUseSystemAccent(AraTheme.isUseSystemAccent());
        settingsStorage.save(appSettings);
    }

    private void applySettings(AppSettings settings) {
        appSettings.setTemperature(settings.getTemperature());
        appSettings.setMaxTokens(settings.getMaxTokens());
        appSettings.setSystemPrompt(settings.getSystemPrompt());
        appSettings.setDarkMode(settings.isDarkMode());
        appSettings.setSelectedModel(settings.getSelectedModel());
        appSettings.setUseSystemAccent(settings.isUseSystemAccent());

        config.setTemperature(appSettings.getTemperature());
        config.setMaxTokens(appSettings.getMaxTokens());
        config.setSystemPrompt(appSettings.getSystemPrompt());
        AraTheme.setUseSystemAccent(appSettings.isUseSystemAccent());
        AraTheme.setDark(appSettings.isDarkMode());
    }

    private void makeSidebarDraggable(Region sidebarNode) {
        var startX = new double[1];
        var startWidth = new double[1];
        var dragging = new boolean[1];

        sidebarNode.setOnMouseMoved(e -> {
            var nearEdge = e.getX() >= sidebarNode.getWidth() - 6;
            sidebarNode.setCursor(nearEdge ? Cursor.H_RESIZE : Cursor.DEFAULT);
        });

        sidebarNode.setOnMousePressed(e -> {
            if (e.getX() >= sidebarNode.getWidth() - 6) {
                startX[0] = e.getScreenX();
                startWidth[0] = sidebarNode.getWidth();
                dragging[0] = true;
                e.consume();
            }
        });

        sidebarNode.setOnMouseDragged(e -> {
            if (dragging[0]) {
                var delta = e.getScreenX() - startX[0];
                var newWidth = Math.clamp(startWidth[0] + delta, 158, 400);
                sidebarNode.setPrefWidth(newWidth);
                e.consume();
            }
        });

        sidebarNode.setOnMouseReleased(e -> {
            dragging[0] = false;
        });
    }

}
