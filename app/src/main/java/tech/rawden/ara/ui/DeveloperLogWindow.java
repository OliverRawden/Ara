package tech.rawden.ara.ui;

import tech.rawden.ara.core.AppLog;
import tech.rawden.ara.platform.MacWindow;
import tech.rawden.ara.util.OsType;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.logging.Level;

/** Live developer log window with full in-memory diagnostic history. */
public final class DeveloperLogWindow {

    private static Stage stage;
    private static TextArea logArea;
    private static ComboBox<String> processFilter;
    private static ComboBox<String> levelFilter;
    private static TextField textFilter;
    private static CheckBox autoScroll;
    private static Label countLabel;

    private static final java.util.function.Consumer<AppLog.Entry> LISTENER = DeveloperLogWindow::onEntry;

    private DeveloperLogWindow() {}

    public static void show(Window owner) {
        Platform.runLater(() -> {
            ensureStage(owner);
            refresh();
            stage.show();
            stage.toFront();
            AppLog.removeListener(LISTENER);
            AppLog.addListener(LISTENER);
        });
    }

    public static void hide() {
        Platform.runLater(() -> {
            AppLog.removeListener(LISTENER);
            if (stage != null) {
                stage.hide();
            }
        });
    }

    public static boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    public static void dispose() {
        Platform.runLater(() -> {
            AppLog.removeListener(LISTENER);
            if (stage != null) {
                stage.close();
                stage = null;
                logArea = null;
            }
        });
    }

    private static void ensureStage(Window owner) {
        if (stage != null) {
            if (owner != null) {
                stage.initOwner(owner);
            }
            return;
        }

        stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle("Ara Developer Log");
        stage.setMinWidth(720);
        stage.setMinHeight(420);
        stage.setWidth(960);
        stage.setHeight(560);

        var root = new VBox(10);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("ara-dev-log-root");

        var headline = new Label("Live diagnostic log");
        headline.setFont(Font.font("Inter", FontWeight.BOLD, 14));

        processFilter = new ComboBox<>();
        processFilter.getItems().addAll(AppLog.knownProcesses());
        processFilter.setValue("all");
        processFilter.setPrefWidth(140);
        processFilter.setOnAction(e -> refresh());

        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("ALL", "FINE+", "INFO+", "WARNING+", "SEVERE");
        levelFilter.setValue("INFO+");
        levelFilter.setPrefWidth(110);
        levelFilter.setOnAction(e -> refresh());

        textFilter = new TextField();
        textFilter.setPromptText("Filter text…");
        textFilter.textProperty().addListener((obs, old, val) -> refresh());
        HBox.setHgrow(textFilter, Priority.ALWAYS);

        autoScroll = new CheckBox("Auto-scroll");
        autoScroll.setSelected(true);

        var filterRow = new HBox(8, new Label("Process:"), processFilter, new Label("Level:"), levelFilter, textFilter);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setFont(Font.font("Menlo", 11));
        logArea.getStyleClass().addAll("ara-input-field", "ara-dev-log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        var copyBtn = new Button("Copy");
        copyBtn.getStyleClass().add("ara-action-btn");
        copyBtn.setOnAction(e -> copyToClipboard());

        var refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("ara-action-btn");
        refreshBtn.setOnAction(e -> refresh());

        countLabel = new Label();
        countLabel.setFont(Font.font("Inter", 11));
        countLabel.setStyle("-fx-text-fill: -color-fg-subtle;");

        var actions = new HBox(8, copyBtn, refreshBtn, autoScroll, countLabel);
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(headline, filterRow, logArea, actions);

        var scene = new Scene(root);
        scene.getStylesheets()
                .add(DeveloperLogWindow.class
                        .getResource("/tech/rawden/ara/resources/style/ara.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.setOnHidden(e -> AppLog.removeListener(LISTENER));

        if (OsType.ofLocal() == OsType.MACOS) {
            MacWindow.applyModernStyle(stage);
        }
    }

    private static void onEntry(AppLog.Entry entry) {
        if (!isShowing() || logArea == null) {
            return;
        }
        if (!matchesCurrentFilters(entry)) {
            updateCount();
            return;
        }
        String line = AppLog.format(entry) + "\n";
        if (autoScroll != null && autoScroll.isSelected()) {
            logArea.appendText(line);
            logArea.setScrollTop(Double.MAX_VALUE);
        } else {
            logArea.appendText(line);
        }
        updateCount();
    }

    private static void refresh() {
        if (logArea == null) {
            return;
        }
        var sb = new StringBuilder();
        int shown = 0;
        String process = processFilter != null ? processFilter.getValue() : "all";
        Level minLevel = minLevel();
        String text = textFilter != null ? textFilter.getText() : "";

        for (var entry : AppLog.entries()) {
            if (!AppLog.matches(entry, process, minLevel)) {
                continue;
            }
            if (text != null && !text.isBlank()) {
                String hay = AppLog.format(entry).toLowerCase();
                if (!hay.contains(text.toLowerCase())) {
                    continue;
                }
            }
            sb.append(AppLog.format(entry)).append('\n');
            shown++;
        }
        logArea.setText(sb.toString());
        if (autoScroll != null && autoScroll.isSelected()) {
            logArea.setScrollTop(Double.MAX_VALUE);
        }
        if (countLabel != null) {
            countLabel.setText(shown + " / " + AppLog.entries().size() + " entries");
        }
    }

    private static void updateCount() {
        if (countLabel != null) {
            countLabel.setText(logArea.getText().split("\n", -1).length + " / " + AppLog.entries().size() + " entries");
        }
    }

    private static boolean matchesCurrentFilters(AppLog.Entry entry) {
        String process = processFilter != null ? processFilter.getValue() : "all";
        if (!AppLog.matches(entry, process, minLevel())) {
            return false;
        }
        String text = textFilter != null ? textFilter.getText() : "";
        if (text != null && !text.isBlank()) {
            return AppLog.format(entry).toLowerCase().contains(text.toLowerCase());
        }
        return true;
    }

    private static Level minLevel() {
        String val = levelFilter != null ? levelFilter.getValue() : "INFO+";
        if (val == null) {
            return Level.INFO;
        }
        return switch (val) {
            case "FINE+" -> Level.FINE;
            case "WARNING+" -> Level.WARNING;
            case "SEVERE" -> Level.SEVERE;
            case "ALL" -> Level.ALL;
            default -> Level.INFO;
        };
    }

    private static void copyToClipboard() {
        if (logArea == null) {
            return;
        }
        var content = new ClipboardContent();
        content.putString(logArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
    }

}