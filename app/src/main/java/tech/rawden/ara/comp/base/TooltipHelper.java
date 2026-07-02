package tech.rawden.ara.comp.base;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public class TooltipHelper {

    public static Tooltip create(String text, String shortcut) {
        var tt = new Tooltip(text + (shortcut != null ? " (" + shortcut + ")" : ""));
        tt.setShowDelay(Duration.millis(300));
        tt.setHideDelay(Duration.millis(100));
        return tt;
    }

    public static void install(Node node, String text) {
        var tt = create(text, null);
        Tooltip.install(node, tt);
    }
}
