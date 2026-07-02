package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionBuilder;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import atlantafx.base.controls.ToggleSwitch;

public class ToggleSwitchComp extends RegionBuilder<ToggleSwitch> {

    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final String label;

    public ToggleSwitchComp() {
        this("");
    }

    public ToggleSwitchComp(String label) {
        this.label = label != null ? label : "";
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    @Override
    public ToggleSwitch createSimple() {
        var toggle = new ToggleSwitch(label);
        toggle.getStyleClass().add("toggle-switch-comp");
        toggle.setSelected(selected.get());
        selected.addListener((obs, old, val) -> toggle.setSelected(val));
        toggle.selectedProperty().addListener((obs, old, val) -> selected.set(val));
        return toggle;
    }
}
