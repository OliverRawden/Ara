package tech.rawden.ara.update;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.logging.Logger;

/** Friendly dialog shown when a newer version is available. */
public final class UpdateDialog {

    private static final Logger LOG = Logger.getLogger(UpdateDialog.class.getName());

    private UpdateDialog() {}

    public static void showAvailable(UpdateInfo info, UpdateService updateService) {
        var dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Update Available");

        var root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setPrefWidth(520);

        var headline = new Label("Ara " + info.latestVersion() + " is available");
        headline.setFont(Font.font("Inter", FontWeight.BOLD, 16));

        var subtitle = new Label("You are running Ara " + info.currentVersion()
                + (info.releaseDate() != null && !info.releaseDate().isBlank()
                        ? "  •  Released " + info.releaseDate()
                        : ""));
        subtitle.setFont(Font.font("Inter", 12));
        subtitle.setStyle("-fx-text-fill: -color-fg-subtle;");
        subtitle.setWrapText(true);

        var notesLabel = new Label("What's new");
        notesLabel.setFont(Font.font("Inter", FontWeight.SEMI_BOLD, 12));

        var notes = new TextArea(info.releaseNotes());
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefRowCount(6);
        notes.getStyleClass().add("ara-input-field");

        var progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);

        var status = new Label();
        status.setFont(Font.font("Inter", 11));
        status.setStyle("-fx-text-fill: -color-fg-subtle;");
        status.setWrapText(true);

        var downloadBtn = new Button("Download & Install");
        downloadBtn.getStyleClass().add("ara-action-btn");
        downloadBtn.setDefaultButton(true);

        var laterBtn = new Button("Not Now");
        laterBtn.getStyleClass().add("ara-action-btn");
        laterBtn.setOnAction(e -> dialog.close());

        downloadBtn.setOnAction(e -> {
            downloadBtn.setDisable(true);
            laterBtn.setDisable(true);
            progress.setVisible(true);
            progress.setProgress(-1);
            status.setText("Downloading installer…");

            Thread.startVirtualThread(() -> {
                try {
                    updateService.downloadAndLaunchInstaller(
                            info.downloadUrl(),
                            (downloaded, total) -> Platform.runLater(() -> {
                                if (total > 0) {
                                    progress.setProgress((double) downloaded / total);
                                } else {
                                    progress.setProgress(-1);
                                }
                            }));
                    Platform.runLater(() -> {
                        status.setText(
                                "Installer downloaded. Follow the on-screen steps to complete the update, then restart Ara.");
                        progress.setProgress(1);
                        downloadBtn.setText("Downloaded");
                    });
                } catch (Exception ex) {
                    LOG.warning("Update download failed: " + ex.getMessage());
                    Platform.runLater(() -> {
                        status.setText("Download failed: " + ex.getMessage());
                        progress.setVisible(false);
                        downloadBtn.setDisable(false);
                        downloadBtn.setText("Retry Download");
                        laterBtn.setDisable(false);
                    });
                }
            });
        });

        var buttons = new HBox(10, downloadBtn, laterBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        var privacy =
                new Label("Update checks only contact GitHub for version metadata. Nothing is sent from your device.");
        privacy.setFont(Font.font("Inter", 10));
        privacy.setStyle("-fx-text-fill: -color-fg-subtle;");
        privacy.setWrapText(true);

        root.getChildren().addAll(headline, subtitle, notesLabel, notes, progress, status, buttons, privacy);
        VBox.setVgrow(notes, Priority.SOMETIMES);

        dialog.setScene(new javafx.scene.Scene(root));
        dialog.show();
    }

    public static void showUpToDate(String currentVersion) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("No Updates");
        alert.setHeaderText("You're up to date");
        alert.setContentText("Ara " + currentVersion + " is the latest version available.");
        alert.show();
    }

    public static void showCheckFailed(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update Check");
        alert.setHeaderText("Could not check for updates");
        alert.setContentText(message);
        alert.show();
    }
}
