package tech.rawden.ara.core;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;

/** App-wide navigation state: current view (chat vs settings). Singleton via {@link #init()}. */
public class AraModel {

    public enum View {
        CHAT,
        SETTINGS
    }

    private static AraModel INSTANCE;

    private final Property<View> currentView = new SimpleObjectProperty<>(View.CHAT);

    public AraModel() {}

    public static AraModel get() {
        return INSTANCE;
    }

    public static void init() {
        INSTANCE = new AraModel();
    }

    public static void reset() {
        INSTANCE = null;
    }

    public Property<View> currentViewProperty() {
        return currentView;
    }

    public View currentView() {
        return currentView.getValue();
    }

    public void selectView(View view) {
        currentView.setValue(view);
    }
}
