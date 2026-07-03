package tech.rawden.ara.ui;

import tech.rawden.ara.ai.ModelRouter;
import tech.rawden.ara.ai.ModelTier;
import tech.rawden.ara.ai.RoutingMode;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Compact model badge + power toggle for the chat input bar. Binds to {@link ModelRouter} properties.
 */
public final class ModelStatusControl extends HBox {

    private final ModelRouter router;
    private final Circle indicator;
    private final Label badgeLabel;
    private final Button powerBtn;
    private Runnable onRoutingChanged;

    public ModelStatusControl(ModelRouter router) {
        this.router = router;
        setAlignment(Pos.CENTER);
        setSpacing(6);
        getStyleClass().add("ara-model-status");

        indicator = new Circle(4);
        indicator.getStyleClass().add("ara-model-indicator");

        badgeLabel = new Label();
        badgeLabel.setFont(Font.font("Inter", 11));
        badgeLabel.getStyleClass().add("ara-model-badge-label");

        var badgeBox = new HBox(6, indicator, badgeLabel);
        badgeBox.setAlignment(Pos.CENTER_LEFT);
        badgeBox.getStyleClass().add("ara-model-badge");
        badgeBox.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                showContextMenu(badgeBox);
            }
        });
        var badgeTooltip = new javafx.scene.control.Tooltip();
        badgeTooltip.textProperty().bind(router.badgeDetailProperty());
        javafx.scene.control.Tooltip.install(badgeBox, badgeTooltip);

        powerBtn = new Button();
        powerBtn.getStyleClass().add("ara-power-toggle");
        powerBtn.setTooltip(new javafx.scene.control.Tooltip("Toggle Auto ↔ Heavy (right-click for menu)"));
        updatePowerIcon();
        powerBtn.setOnAction(e -> {
            router.toggleAutoHeavy();
            updatePowerIcon();
            notifyChanged();
        });
        powerBtn.setOnContextMenuRequested(e -> showContextMenu(powerBtn));

        getChildren().addAll(badgeBox, powerBtn);

        router.badgeLabelProperty().addListener((obs, old, val) -> badgeLabel.setText(val));
        router.activeTierProperty().addListener((obs, old, tier) -> updateIndicator(tier));
        router.routingModeProperty().addListener((obs, old, mode) -> updatePowerIcon());

        badgeLabel.setText(router.badgeLabelProperty().get());
        updateIndicator(router.getActiveTier());
    }

    public void setOnRoutingChanged(Runnable onRoutingChanged) {
        this.onRoutingChanged = onRoutingChanged;
    }

    private void notifyChanged() {
        if (onRoutingChanged != null) {
            onRoutingChanged.run();
        }
    }

    private void updateIndicator(ModelTier tier) {
        indicator.getStyleClass().removeAll("ara-model-indicator-light", "ara-model-indicator-heavy");
        if (tier == ModelTier.HEAVY) {
            indicator.getStyleClass().add("ara-model-indicator-heavy");
        } else {
            indicator.getStyleClass().add("ara-model-indicator-light");
        }
    }

    private void updatePowerIcon() {
        RoutingMode mode = router.getCurrentRoutingMode();
        boolean heavy = mode == RoutingMode.HEAVY_ONLY;
        var icon = new FontIcon(heavy ? "mdi2f-flash" : "mdi2f-flash-outline");
        icon.setIconSize(16);
        powerBtn.setGraphic(icon);
        powerBtn.getStyleClass().remove("ara-power-active");
        if (heavy) {
            powerBtn.getStyleClass().add("ara-power-active");
        }
        powerBtn.setTooltip(new javafx.scene.control.Tooltip(
                heavy ? "Heavy forced — click for Auto" : "Auto routing — click for Heavy"));
    }

    private void showContextMenu(Region anchor) {
        var menu = new ContextMenu();

        var lightTurn = new MenuItem("Use Light for this turn");
        lightTurn.setOnAction(e -> {
            router.setSingleTurnOverride(RoutingMode.LIGHT_ONLY);
            notifyChanged();
        });

        var heavyTurn = new MenuItem("Use Heavy for this turn");
        heavyTurn.setOnAction(e -> {
            router.setSingleTurnOverride(RoutingMode.HEAVY_ONLY);
            notifyChanged();
        });

        var alwaysHeavy = new MenuItem("Always use Heavy this session");
        alwaysHeavy.setOnAction(e -> {
            router.setUserOverride(RoutingMode.HEAVY_ONLY);
            updatePowerIcon();
            notifyChanged();
        });

        var resetAuto = new MenuItem("Reset to Auto");
        resetAuto.setOnAction(e -> {
            router.setUserOverride(RoutingMode.AUTO);
            updatePowerIcon();
            notifyChanged();
        });

        menu.getItems().addAll(lightTurn, heavyTurn, alwaysHeavy, resetAuto);
        menu.show(anchor, javafx.geometry.Side.TOP, 0, -4);
    }
}