package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionStructure;
import tech.rawden.ara.comp.RegionStructureBuilder;
import tech.rawden.ara.platform.PlatformThread;

import javafx.beans.value.ObservableValue;
import javafx.scene.layout.StackPane;

import lombok.Value;
import org.kordamp.ikonli.javafx.FontIcon;

public class FontIconComp extends RegionStructureBuilder<StackPane, FontIconComp.Structure> {

    private final ObservableValue<String> icon;

    public FontIconComp(ObservableValue<String> icon) {
        this.icon = icon;
    }

    @Override
    public Structure createBase() {
        var fi = new FontIcon();
        icon.subscribe(val -> PlatformThread.runLaterIfNeeded(() -> fi.setIconLiteral(val)));
        var pane = new StackPane(fi);
        return new Structure(fi, pane);
    }

    @Value
    public static class Structure implements RegionStructure<StackPane> {
        FontIcon icon;
        StackPane pane;

        @Override
        public StackPane get() {
            return pane;
        }
    }
}
