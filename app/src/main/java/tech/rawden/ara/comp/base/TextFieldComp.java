package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionBuilder;
import tech.rawden.ara.platform.PlatformThread;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;

public class TextFieldComp extends RegionBuilder<TextField> {

    private final ObservableValue<String> text;

    public TextFieldComp(ObservableValue<String> text) {
        this.text = text;
    }

    @Override
    public TextField createSimple() {
        var tf = new TextField();
        tf.setPromptText("Type a message...");
        text.subscribe(t -> PlatformThread.runLaterIfNeeded(() -> tf.setText(t)));
        return tf;
    }
}
