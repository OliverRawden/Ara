package tech.rawden.ara;

import tech.rawden.ara.ai.LlamaCppInferenceService;
import tech.rawden.ara.ai.ModelManager;
import tech.rawden.ara.ai.ModelPreloader;
import tech.rawden.ara.core.AraModel;
import tech.rawden.ara.core.AraPaths;
import tech.rawden.ara.core.AraTheme;
import tech.rawden.ara.core.MacMenuBar;
import tech.rawden.ara.core.ShortcutManager;
import tech.rawden.ara.model.AppSettings;
import tech.rawden.ara.model.ChatHistory;
import tech.rawden.ara.model.ChatStorage;
import tech.rawden.ara.model.InferenceConfig;
import tech.rawden.ara.model.SettingsStorage;
import tech.rawden.ara.platform.MacWindow;
import tech.rawden.ara.tool.ToolCatalog;
import tech.rawden.ara.ui.MainViewComp;
import tech.rawden.ara.util.OsType;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.logging.Logger;

/**
 * JavaFX entry point. Startup is staged for responsiveness:
 * <ol>
 *   <li>Splash window → full UI shell (empty chat history)</li>
 *   <li>Encryption unlock dialog if needed (PBKDF2 on virtual thread)</li>
 *   <li>Parallel background work: chat decrypt/load + GGUF model preload</li>
 * </ol>
 * Model preload never blocks window appearance; see {@link ModelPreloader}.
 */
public class Main extends Application {

    public static final String VERSION = "6.0.x";

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private LlamaCppInferenceService inferenceService;

    @Override
    public void start(Stage stage) {
        AraModel.init();
        AraTheme.init();
        ToolCatalog.reload();

        var settingsStorage = new SettingsStorage();
        var appSettings = settingsStorage.load();
        AraTheme.setUseSystemAccent(appSettings.isUseSystemAccent());
        AraTheme.setDark(appSettings.isDarkMode());

        // Privacy: initialize encryption service from persisted preference
        tech.rawden.ara.core.SecurityService.setEncryptionEnabled(appSettings.isEncryptionEnabled());

        // Show a *trivial* window *immediately* so the app appears in the dock right away.
        // This is before any MainViewComp construction or model auto-load.
        // The rest of the (light for empty) UI build below will quickly replace the content.
        // This solves the "no window for minutes" even when model load or encryption is slow.
        var logo = tech.rawden.ara.platform.AraLogo.createNode(48);
        var title = new javafx.scene.text.Text("Ara");
        title.setFont(javafx.scene.text.Font.font("Inter", javafx.scene.text.FontWeight.BOLD, 24));
        title.setFill(javafx.scene.paint.Color.web("#cccccc"));
        var box = new javafx.scene.layout.HBox(12, logo, title);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        var trivialPane = new StackPane(box);
        trivialPane.setStyle("-fx-background-color: #151515;");
        stage.setTitle("Ara");
        stage.setScene(new Scene(trivialPane, 1100, 720));
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.show();

        if (OsType.ofLocal() == OsType.MACOS) {
            Platform.runLater(() -> MacWindow.applyModernStyle(stage));
        }

        // Use a temporary empty history for initial UI construction so we can show the window immediately.
        // Real data will be loaded asynchronously (in separate threads) and the UI updated via Platform.runLater.
        var initialChatHistory = new ChatHistory();

        var chatStorage = new ChatStorage();
        var config = new InferenceConfig();
        config.setTemperature(appSettings.getTemperature());
        config.setMaxTokens(appSettings.getMaxTokens());
        config.setSystemPrompt(appSettings.getSystemPrompt());
        config.setWebSearchEnabled(appSettings.isWebSearchEnabled());
        config.setTerminalEnabled(appSettings.isTerminalEnabled());
        config.setContextMemoryEnabled(appSettings.isContextMemoryEnabled());
        config.setRequireTerminalConfirmation(appSettings.isRequireTerminalConfirmation());

        inferenceService = new LlamaCppInferenceService();
        var modelManager = new ModelManager();
        var modelPreloader = new ModelPreloader(inferenceService, modelManager);

        var mainView = new MainViewComp(
                AraModel.get(),
                initialChatHistory,
                inferenceService,
                modelManager,
                modelPreloader,
                config,
                chatStorage,
                settingsStorage,
                appSettings);

        var contentPane = new StackPane(mainView.build());
        contentPane.getStyleClass().add("root");

        var actions = new ShortcutManager.Actions() {
            @Override
            public void newChat() {
                mainView.newChat();
            }

            @Override
            public void closeChat() {
                mainView.closeChat();
            }

            @Override
            public void quitApp() {
                stage.close();
            }

            @Override
            public void toggleSettings() {
                var model = AraModel.get();
                model.selectView(
                        model.currentView() == AraModel.View.SETTINGS ? AraModel.View.CHAT : AraModel.View.SETTINGS);
            }
        };

        var scene = new Scene(contentPane, 1100, 720);
        scene.getStylesheets()
                .addAll(
                        getClass()
                                .getResource("/tech/rawden/ara/resources/style/ara.css")
                                .toExternalForm(),
                        getClass()
                                .getResource("/tech/rawden/ara/resources/font-config/font.css")
                                .toExternalForm());

        if (OsType.ofLocal() == OsType.MACOS) {
            var menuBar = MacMenuBar.create(actions);
            menuBar.setPrefHeight(0);
            menuBar.setMinHeight(0);

            var borderPane = new BorderPane();
            borderPane.setTop(menuBar);
            borderPane.setCenter(contentPane);
            scene.setRoot(borderPane);
            scene.setFill(Color.TRANSPARENT);
            contentPane.pseudoClassStateChanged(PseudoClass.getPseudoClass("macos"), true);

            var dragDelta = new double[2];
            var dragActive = new boolean[1];
            borderPane.setOnMousePressed(e -> {
                if (e.getY() <= 28) {
                    dragDelta[0] = e.getScreenX() - stage.getX();
                    dragDelta[1] = e.getScreenY() - stage.getY();
                    dragActive[0] = true;
                }
            });
            borderPane.setOnMouseDragged(e -> {
                if (dragActive[0]) {
                    stage.setX(e.getScreenX() - dragDelta[0]);
                    stage.setY(e.getScreenY() - dragDelta[1]);
                }
            });
            borderPane.setOnMouseReleased(e -> dragActive[0] = false);
        } else {
            new ShortcutManager(scene, actions);
        }

        var accentURL = new String[] {null};
        if (AraTheme.isUseSystemAccent()) {
            accentURL[0] = AraTheme.getAccentStylesheetURL();
            scene.getStylesheets().add(accentURL[0]);
        }
        AraTheme.setOnStyleChanged(() -> {
            scene.getStylesheets().remove(accentURL[0]);
            accentURL[0] = AraTheme.getAccentStylesheetURL();
            if (accentURL[0] != null) {
                scene.getStylesheets().add(accentURL[0]);
            }
        });

        stage.setMinWidth(800);
        stage.setMinHeight(500);

        stage.setOnCloseRequest(e -> {
            // The chatHistory reference in MainViewComp will be the up-to-date one after async load
            // For safety, we can rely on MainViewComp's internal handling or re-save here if needed.
            appSettings.setDarkMode(AraTheme.isDark());
            appSettings.setUseSystemAccent(AraTheme.isUseSystemAccent());
            settingsStorage.save(appSettings);
        });

        stage.setScene(scene);

        if (OsType.ofLocal() == OsType.MACOS) {
            Platform.runLater(() -> MacWindow.applyModernStyle(stage));
        }

        Runnable loadRealChats = () -> Thread.ofVirtual().name("ara-data-loader").start(() -> {
            try {
                ChatHistory realHistory = chatStorage.load();
                Platform.runLater(() -> mainView.updateChatHistory(realHistory));
            } catch (Exception ex) {
                LOG.warning("Data load failed: " + ex.getMessage());
            }
        });

        Runnable onSessionReady = () -> {
            loadRealChats.run();
            modelPreloader.schedulePreload(appSettings.getSelectedModel());
        };

        if (appSettings.isEncryptionEnabled()) {
            showUnlockDialogNonBlocking(appSettings, settingsStorage, onSessionReady);
        } else {
            onSessionReady.run();
        }
    }

    @Override
    public void stop() {
        if (inferenceService != null) {
            var shutdown = new Thread(inferenceService::shutdown);
            shutdown.setDaemon(true);
            shutdown.start();
            try {
                shutdown.join(3000);
            } catch (InterruptedException ignored) {
            }
        }
        System.exit(0);
    }

    private void promptForUnlockPassphrase(AppSettings appSettings, SettingsStorage settingsStorage) {
        // Show modal unlock dialog directly (we are on the FX application thread).
        // This properly blocks until the user enters the correct password or explicitly resets.
        // All encrypted data loads happen AFTER this returns, so the key is guaranteed to be set.
        var dialog = new javafx.stage.Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("Unlock Encrypted Data");

        var root = new javafx.scene.layout.VBox(10);
        root.setPadding(new javafx.geometry.Insets(16));
        root.setPrefWidth(420);

        var label = new javafx.scene.control.Label(
                "Encryption is enabled. Enter your privacy passphrase to decrypt chats, memory, and logs for this session.\n\n"
                        + "If you enter the wrong passphrase, decryption will fail and you will see an error. "
                        + "There is no automatic fallback to empty data.");
        label.setWrapText(true);

        var pf = new javafx.scene.control.PasswordField();
        pf.setPromptText("Privacy passphrase");

        var status = new javafx.scene.control.Label();
        status.setStyle("-fx-text-fill: #cc0000; -fx-font-weight: bold;");

        var unlock = new javafx.scene.control.Button("Unlock");
        unlock.setDefaultButton(true);

        var forgotBtn =
                new javafx.scene.control.Button("I forgot my passphrase - Start fresh (deletes all encrypted data)");
        forgotBtn.setStyle("-fx-text-fill: #cc0000;");

        unlock.setOnAction(e -> {
            char[] pass = pf.getText() != null ? pf.getText().toCharArray() : new char[0];
            tech.rawden.ara.core.SecurityService.unlockWithPassphrase(pass);

            if (!tech.rawden.ara.core.SecurityService.isUnlocked()) {
                status.setText("Could not derive key. Please try again.");
                return;
            }

            // Validate against existing encrypted data if present.
            // This ensures we only close on a key that can actually decrypt.
            boolean looksGood = true;
            try {
                var chatsFile = new tech.rawden.ara.model.ChatStorage().chatsFile();
                if (java.nio.file.Files.exists(chatsFile)) {
                    byte[] raw = java.nio.file.Files.readAllBytes(chatsFile);
                    tech.rawden.ara.core.SecurityService.decryptStrict(raw); // throws on wrong key
                }
            } catch (Exception ex) {
                looksGood = false;
            }

            if (looksGood) {
                dialog.close();
            } else {
                status.setText("Wrong passphrase — the key could not decrypt your data.");
                tech.rawden.ara.core.SecurityService.lock();
            }
        });

        forgotBtn.setOnAction(e -> {
            Alert confirm = new Alert(
                    Alert.AlertType.WARNING,
                    "This will permanently and securely delete all your encrypted chats, memory/context, and audit logs.\n\n"
                            + "You will start with a completely fresh (empty) session. This cannot be undone.\n\nContinue?",
                    ButtonType.YES,
                    ButtonType.NO);
            confirm.setTitle("Forgot Passphrase - Destructive Action");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    new tech.rawden.ara.model.ChatStorage().delete();
                    tech.rawden.ara.core.SecurityService.secureDelete(
                            new tech.rawden.ara.model.ChatStorage().chatsFile());
                    tech.rawden.ara.core.SecurityService.secureDelete(AraPaths.contextFile());
                    new tech.rawden.ara.model.AuditLogStorage().delete();

                    tech.rawden.ara.core.SecurityService.lock();
                    appSettings.setEncryptionEnabled(false);
                    settingsStorage.save(appSettings);

                    status.setText("Encrypted data deleted. Starting fresh.");
                    dialog.close();
                }
            });
        });

        var help = new javafx.scene.control.Label(
                "Tip: Passphrases must be at least 6 characters. Key derivation takes a moment (PBKDF2).");
        help.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        help.setWrapText(true);

        root.getChildren().addAll(label, pf, status, unlock, forgotBtn, help);
        dialog.setScene(new javafx.scene.Scene(root));
        dialog.showAndWait();
        // When we reach here the dialog has been closed (either with valid key or after reset).
    }

    /**
     * Show the unlock dialog in a non-blocking way so the main app window (already shown with placeholder data)
     * remains responsive and visible immediately. Uses a dedicated virtual thread for the CPU-heavy PBKDF2 key derivation
     * (different thread for the derivation process).
     * On successful unlock, the provided loadRealChats runnable is executed (in its own virtual thread for I/O) to load
     * and decrypt data, then update the UI on the FX thread.
     */
    private void showUnlockDialogNonBlocking(
            AppSettings appSettings, SettingsStorage settingsStorage, Runnable onUnlockedLoad) {
        var dialog = new javafx.stage.Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("Unlock Encrypted Data");

        var root = new javafx.scene.layout.VBox(10);
        root.setPadding(new javafx.geometry.Insets(16));
        root.setPrefWidth(420);

        var label = new javafx.scene.control.Label(
                "Encryption is enabled. Enter your privacy passphrase to decrypt chats, memory, and logs for this session.\n\n"
                        + "The main app window is already visible (launched quickly with placeholder data for fast startup). "
                        + "Real data will load and decrypt in the background after unlock.");
        label.setWrapText(true);

        var pf = new javafx.scene.control.PasswordField();
        pf.setPromptText("Privacy passphrase");

        var status = new javafx.scene.control.Label();
        status.setStyle("-fx-text-fill: #cc0000; -fx-font-weight: bold;");

        var unlockBtn = new javafx.scene.control.Button("Unlock");
        unlockBtn.setDefaultButton(true);

        var forgotBtn =
                new javafx.scene.control.Button("I forgot my passphrase - Start fresh (deletes all encrypted data)");
        forgotBtn.setStyle("-fx-text-fill: #cc0000;");

        unlockBtn.setOnAction(e -> {
            String passText = pf.getText();
            if (passText == null || passText.isEmpty()) {
                status.setText("Please enter a passphrase.");
                return;
            }

            unlockBtn.setDisable(true);
            forgotBtn.setDisable(true);
            status.setText("Deriving key (PBKDF2 - using background thread)...");
            status.setStyle("-fx-text-fill: gray;");

            // KEY MULTI-THREADING: Run the slow PBKDF2 derivation on its own virtual thread.
            // This is a separate "process" from the FX thread and from data loading.
            // Prevents freezing the password dialog or the main app window during derivation.
            Thread.startVirtualThread(() -> {
                char[] pass = passText.toCharArray();
                tech.rawden.ara.core.SecurityService.unlockWithPassphrase(pass);

                Platform.runLater(() -> {
                    if (!tech.rawden.ara.core.SecurityService.isUnlocked()) {
                        status.setText("Could not derive key. Please try again.");
                        status.setStyle("-fx-text-fill: #cc0000; -fx-font-weight: bold;");
                        unlockBtn.setDisable(false);
                        forgotBtn.setDisable(false);
                        return;
                    }

                    status.setText("Unlocked! Loading decrypted data in background...");
                    // Trigger the data load (which starts yet another virtual thread for I/O and Jackson deserial).
                    // Different threads for derivation vs I/O vs FX.
                    onUnlockedLoad.run();
                    dialog.close();
                });
            });
        });

        forgotBtn.setOnAction(e -> {
            Alert confirm = new Alert(
                    Alert.AlertType.WARNING,
                    "This will permanently and securely delete all your encrypted chats, memory/context, and audit logs.\n\n"
                            + "You will start with a completely fresh (empty) session. This cannot be undone.\n\nContinue?",
                    ButtonType.YES,
                    ButtonType.NO);
            confirm.setTitle("Forgot Passphrase - Destructive Action");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    new tech.rawden.ara.model.ChatStorage().delete();
                    tech.rawden.ara.core.SecurityService.secureDelete(
                            new tech.rawden.ara.model.ChatStorage().chatsFile());
                    tech.rawden.ara.core.SecurityService.secureDelete(AraPaths.contextFile());
                    new tech.rawden.ara.model.AuditLogStorage().delete();

                    tech.rawden.ara.core.SecurityService.lock();
                    appSettings.setEncryptionEnabled(false);
                    settingsStorage.save(appSettings);

                    status.setText("Encrypted data deleted. Starting fresh.");
                    onUnlockedLoad.run(); // loads empty in its thread
                    dialog.close();
                }
            });
        });

        var help = new javafx.scene.control.Label(
                "Tip: Passphrases must be at least 6 characters. Key derivation and data load use dedicated background (virtual) threads for a responsive UI.");
        help.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        help.setWrapText(true);

        root.getChildren().addAll(label, pf, status, unlockBtn, forgotBtn, help);
        dialog.setScene(new javafx.scene.Scene(root));
        dialog.show(); // non-blocking so the main app window (shown earlier in start()) is immediately visible and
        // usable.
    }
}
