package io.abc_def.kickstart_fx.page;

import io.abc_def.kickstart_fx.comp.SimpleRegionBuilder;

import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import com.oracle.tools.fx.monkey.MainWindow;

public class MonkeyTesterPageComp extends SimpleRegionBuilder {

    @Override
    @SuppressWarnings("unchecked")
    protected Region createSimple() {
        var stack = new StackPane();
        stack.visibleProperty().subscribe(v -> {
            if (!v || !stack.getChildren().isEmpty()) {
                return;
            }

            var monkeyTesterWindow = new MainWindow();
            var bp = (BorderPane) monkeyTesterWindow.getScene().getRoot();
            var menuBar = (MenuBar) bp.getTop();
            menuBar.getStyleClass().add("background");

            var pane = (SplitPane) bp.getCenter();
            pane.setDividerPosition(0, 0.7);

            var lv = (ListView<Object>) pane.getItems().getFirst();
            lv.setCellFactory(null);

            stack.getChildren().add(bp);
        });
        return stack;
    }
}
