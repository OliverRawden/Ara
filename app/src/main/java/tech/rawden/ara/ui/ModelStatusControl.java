package tech.rawden.ara.ui;

import tech.rawden.ara.ai.ModelRouter;
import tech.rawden.ara.ai.ModelTier;
import tech.rawden.ara.ai.RoutingMode;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Compact routing chip above the chat input — click toggles Auto ↔ Heavy; right-click for full menu.
 */
public final class ModelStatusControl extends Region {

    private final ModelRouter router;
    private final Button chip;
    private final Circle indicator;
    private final FontIcon modeIcon;
    private final Label chipLabel;
    private Runnable onRoutingChanged;

    public ModelStatusControl(ModelRouter router) {
        this.router = router;
        getStyleClass().add("ara-model-chip-host");

        indicator = new Circle(2.5);
        indicator.getStyleClass().add("ara-model-indicator");

        modeIcon = new FontIcon("mdi2a-auto-fix");
        modeIcon.setIconSize(9);

        chipLabel = new Label("Auto");
        chipLabel.getStyleClass().add("ara-model-chip-label");

        var content = new HBox(3, indicator, modeIcon, chipLabel);
        content.setAlignment(Pos.CENTER);

        chip = new Button();
        chip.getStyleClass().add("ara-model-chip");
        chip.setGraphic(content);
        chip.setMinHeight(36);
        chip.setPrefHeight(36);
        chip.setMaxHeight(36);
        chip.setOnAction(e -> {
            router.toggleAutoHeavy();
            refresh();
            notifyChanged();
        });
        chip.setOnContextMenuRequested(e -> showContextMenu());

        var tooltip = new Tooltip();
        tooltip.textProperty().bind(router.badgeDetailProperty());
        Tooltip.install(chip, tooltip);

        getChildren().add(chip);
        setMaxHeight(36);
        setPrefHeight(36);

        router.activeTierProperty().addListener((obs, old, tier) -> refresh());
        router.routingModeProperty().addListener((obs, old, mode) -> refresh());

        refresh();
    }

    public void setOnRoutingChanged(Runnable onRoutingChanged) {
        this.onRoutingChanged = onRoutingChanged;
    }

    private void refresh() {
        RoutingMode mode = router.getCurrentRoutingMode();
        ModelTier tier = router.getActiveTier();

        indicator.getStyleClass().removeAll("ara-model-indicator-light", "ara-model-indicator-heavy");
        chip.getStyleClass().removeAll("ara-model-chip-auto", "ara-model-chip-heavy");

        if (mode == RoutingMode.HEAVY_ONLY) {
            chipLabel.setText("Advanced");
            modeIcon.setIconLiteral("mdi2f-flash");
            indicator.getStyleClass().add("ara-model-indicator-heavy");
            chip.getStyleClass().add("ara-model-chip-heavy");
        } else if (mode == RoutingMode.LIGHT_ONLY) {
            chipLabel.setText("Fast");
            modeIcon.setIconLiteral("mdi2f-feather");
            indicator.getStyleClass().add("ara-model-indicator-light");
        } else {
            chipLabel.setText("Auto");
            modeIcon.setIconLiteral("mdi2a-auto-fix");
            chip.getStyleClass().add("ara-model-chip-auto");
            if (tier == ModelTier.HEAVY) {
                indicator.getStyleClass().add("ara-model-indicator-heavy");
                chip.getStyleClass().add("ara-model-chip-heavy");
            } else {
                indicator.getStyleClass().add("ara-model-indicator-light");
            }
        }
    }

    private void notifyChanged() {
        if (onRoutingChanged != null) {
            onRoutingChanged.run();
        }
    }

    private void showContextMenu() {
        var menu = new ContextMenu();

        var lightTurn = new MenuItem("Fast model for next message");
        lightTurn.setOnAction(e -> {
            router.setSingleTurnOverride(RoutingMode.LIGHT_ONLY);
            notifyChanged();
        });

        var heavyTurn = new MenuItem("Advanced model for next message");
        heavyTurn.setOnAction(e -> {
            router.setSingleTurnOverride(RoutingMode.HEAVY_ONLY);
            notifyChanged();
        });

        var alwaysHeavy = new MenuItem("Always use advanced model");
        alwaysHeavy.setOnAction(e -> {
            router.setUserOverride(RoutingMode.HEAVY_ONLY);
            refresh();
            notifyChanged();
        });

        var alwaysLight = new MenuItem("Always use fast model");
        alwaysLight.setOnAction(e -> {
            router.setUserOverride(RoutingMode.LIGHT_ONLY);
            refresh();
            notifyChanged();
        });

        var resetAuto = new MenuItem("Automatic routing");
        resetAuto.setOnAction(e -> {
            router.setUserOverride(RoutingMode.AUTO);
            refresh();
            notifyChanged();
        });

        menu.getItems().addAll(lightTurn, heavyTurn, alwaysHeavy, alwaysLight, resetAuto);
        menu.show(chip, javafx.geometry.Side.TOP, 0, -2);
    }
}