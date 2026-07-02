package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionBuilder;
import tech.rawden.ara.platform.LabelGraphic;
import tech.rawden.ara.platform.PlatformThread;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

public class LabelComp extends RegionBuilder<Label> {

    private final ObservableValue<String> text;
    private final ObservableValue<LabelGraphic> graphic;

    public LabelComp(String text) {
        this(new SimpleStringProperty(text));
    }

    public LabelComp(ObservableValue<String> text) {
        this.text = text;
        this.graphic = new SimpleObjectProperty<>();
    }

    @Override
    public Label createSimple() {
        var label = new Label();
        text.subscribe(t -> PlatformThread.runLaterIfNeeded(() -> label.setText(t)));
        graphic.subscribe(
                t -> PlatformThread.runLaterIfNeeded(() -> label.setGraphic(t != null ? t.createGraphicNode() : null)));
        label.setAlignment(Pos.CENTER_LEFT);
        return label;
    }
}
