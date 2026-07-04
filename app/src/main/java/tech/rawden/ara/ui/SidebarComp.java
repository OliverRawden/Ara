package tech.rawden.ara.ui;

import tech.rawden.ara.comp.RegionBuilder;
import tech.rawden.ara.core.AraModel;
import tech.rawden.ara.model.ChatHistory;
import tech.rawden.ara.model.ChatSession;
import tech.rawden.ara.platform.AraLogo;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Left sidebar: logo, new-chat button, session list (capped at 60 rows), settings nav. */
public class SidebarComp extends RegionBuilder<VBox> {

    private static final double SIDEBAR_WIDTH = 280;
    private static final double SIDEBAR_HORIZONTAL_INSET = 6;
    private static final double CHAT_ROW_CONTENT_PADDING = 10;
    private static final double CHAT_ROW_DELETE_BTN_SPACE = 30;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    // Limit sidebar items at startup/build for faster UI construction with large histories.
    // Users can still access older via search or future virtualized list.
    private static final int MAX_VISIBLE_CHATS_IN_SIDEBAR = 60;

    private final AraModel model;
    private ChatHistory chatHistory;
    private final Runnable onNewChat;
    private final Consumer<ChatSession> onSelectSession;
    private final Consumer<ChatSession> onDeleteSession;
    private VBox chatListView;
    private VBox sidebarNode;

    public SidebarComp(
            AraModel model,
            ChatHistory chatHistory,
            Runnable onNewChat,
            Consumer<ChatSession> onSelectSession,
            Consumer<ChatSession> onDeleteSession) {
        this.model = model;
        this.chatHistory = chatHistory;
        this.onNewChat = onNewChat;
        this.onSelectSession = onSelectSession;
        this.onDeleteSession = onDeleteSession;
    }

    public void updateChatHistory(ChatHistory newHistory) {
        this.chatHistory = newHistory;
        rebuildChatList();
    }

    @Override
    protected VBox createSimple() {
        sidebarNode = new VBox();
        sidebarNode.setPrefWidth(SIDEBAR_WIDTH);
        sidebarNode.setMinWidth(158);
        sidebarNode.setMaxWidth(400);
        sidebarNode.getStyleClass().add("ara-sidebar");

        var logoArea = createLogoArea();
        sidebarNode.getChildren().add(logoArea);

        var newChatBtn = createNewChatButton();
        sidebarNode.getChildren().add(newChatBtn);

        var chatListContainer = createChatList();
        VBox.setVgrow(chatListContainer, Priority.ALWAYS);
        sidebarNode.getChildren().add(chatListContainer);

        var settingsBtn = createSettingsButton();
        sidebarNode.getChildren().add(settingsBtn);

        return sidebarNode;
    }

    private Region createLogoArea() {
        var icon = AraLogo.createNode(36);

        var title = new Text("Ara");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 20));

        var hbox = new HBox(10, icon, title);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setPadding(new Insets(4, 20, 4, 12));
        return hbox;
    }

    private Button createNewChatButton() {
        var btn = new Button("New Chat", new FontIcon("mdi2p-plus"));
        btn.getStyleClass().add("ara-new-chat-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPadding(new Insets(10, 20, 10, 20));
        btn.setOnAction(e -> onNewChat.run());
        VBox.setMargin(btn, new Insets(5, SIDEBAR_HORIZONTAL_INSET, 10, SIDEBAR_HORIZONTAL_INSET));
        return btn;
    }

    private Region createChatList() {
        chatListView = new VBox();
        chatListView.setSpacing(2);
        chatListView.getStyleClass().add("ara-chat-list");

        rebuildChatList();

        var scroll = new ScrollPane(chatListView);
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
        scroll.getStyleClass().add("ara-chat-scroll");
        VBox.setMargin(scroll, new Insets(0, SIDEBAR_HORIZONTAL_INSET, 0, SIDEBAR_HORIZONTAL_INSET));
        return scroll;
    }

    public void rebuildChatList() {
        if (chatListView == null) return;
        chatListView.getChildren().clear();
        var sessions = chatHistory.sessions();
        // Limit for startup speed (creating many custom row nodes + bindings is expensive)
        int start = Math.max(0, sessions.size() - MAX_VISIBLE_CHATS_IN_SIDEBAR);
        for (int i = sessions.size() - 1; i >= start; i--) {
            var session = sessions.get(i);
            var item = createChatListItem(session);
            chatListView.getChildren().add(item);
        }
        if (sessions.size() > MAX_VISIBLE_CHATS_IN_SIDEBAR) {
            var more = new Text("Showing latest " + MAX_VISIBLE_CHATS_IN_SIDEBAR + " of " + sessions.size());
            more.setFont(Font.font("Inter", 10));
            more.getStyleClass().add("ara-chat-list-hint");
            more.setWrappingWidth(240);
            VBox.setMargin(more, new Insets(6, 4, 4, 4));
            chatListView.getChildren().add(more);
        }
    }

    private Region createChatListItem(ChatSession session) {
        var textMaxWidth = Bindings.createDoubleBinding(
                () -> sidebarNode.getWidth()
                        - (SIDEBAR_HORIZONTAL_INSET * 2)
                        - (CHAT_ROW_CONTENT_PADDING * 2)
                        - CHAT_ROW_DELETE_BTN_SPACE,
                sidebarNode.widthProperty());

        var titleText = session.title();
        if (titleText == null || titleText.isBlank() || "...".equals(titleText)) {
            titleText = "New Chat";
        }

        var title = new Text(titleText);
        title.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 12));
        title.getStyleClass().add("ara-chat-item-title");
        title.wrappingWidthProperty().bind(textMaxWidth);

        var lastMsg = session.lastMessage();
        var preview = previewText(lastMsg);
        var previewNode = new Text(preview);
        previewNode.setFont(Font.font("Inter", 10));
        previewNode.getStyleClass().add("ara-chat-item-preview");
        previewNode.wrappingWidthProperty().bind(textMaxWidth);

        var timeText =
                new Text(session.createdAt().atZone(ZoneId.systemDefault()).format(TIME_FMT));
        timeText.setFont(Font.font("Inter", 9));
        timeText.getStyleClass().add("ara-chat-item-time");

        var content = new VBox(2, title, previewNode, timeText);
        content.setPadding(new Insets(8, CHAT_ROW_CONTENT_PADDING, 8, CHAT_ROW_CONTENT_PADDING));
        HBox.setHgrow(content, Priority.ALWAYS);

        var deleteIcon = new FontIcon("mdi2c-close");
        deleteIcon.setIconSize(14);
        var deleteBtn = new Button("", deleteIcon);
        deleteBtn.getStyleClass().add("ara-chat-delete-btn");
        deleteBtn.setCursor(Cursor.HAND);
        deleteBtn.setVisible(false);
        deleteBtn.setOnAction(e -> onDeleteSession.accept(session));
        deleteBtn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);

        var row = new HBox(content, deleteBtn);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("ara-chat-item");
        // Highlight if this is the active session
        if (session.id().equals(chatHistory.activeSessionId())) {
            row.getStyleClass().add("ara-chat-item-selected");
        }
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                onSelectSession.accept(session);
            }
        });
        row.setOnMouseEntered(e -> deleteBtn.setVisible(true));
        row.setOnMouseExited(e -> deleteBtn.setVisible(false));

        return row;
    }

    private Button createSettingsButton() {
        var icon = new FontIcon("mdi2c-cog");
        var btn = new Button("  Settings", icon);
        btn.getStyleClass().add("ara-settings-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(12, 20, 12, 20));
        btn.setOnAction(e -> model.selectView(AraModel.View.SETTINGS));
        return btn;
    }

    private static String previewText(tech.rawden.ara.model.ChatMessage lastMsg) {
        if (lastMsg == null || lastMsg.content() == null || lastMsg.content().isBlank()) {
            return "No messages yet";
        }
        if (lastMsg.role() == tech.rawden.ara.model.ChatMessage.Role.TOOL) {
            var content = lastMsg.content().strip();
            if (content.startsWith("web_search:")) {
                return "Web search";
            }
            if (content.startsWith("Current date and time:")) {
                return "Date & time";
            }
            var firstLine = content.lines().findFirst().orElse("Tool result");
            return firstLine.length() > 60 ? firstLine.substring(0, 57) + "…" : firstLine;
        }
        var oneLine = lastMsg.content().replace('\n', ' ').strip();
        return oneLine.length() > 60 ? oneLine.substring(0, 57) + "…" : oneLine;
    }
}
