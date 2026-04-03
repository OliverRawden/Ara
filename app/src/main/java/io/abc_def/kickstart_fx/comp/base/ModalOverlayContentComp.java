package io.abc_def.kickstart_fx.comp.base;

import io.abc_def.kickstart_fx.comp.SimpleRegionBuilder;
import javafx.beans.value.ObservableValue;
import lombok.Getter;

@Getter
public abstract class ModalOverlayContentComp extends SimpleRegionBuilder {

    protected ModalOverlay modalOverlay;

    protected void setModalOverlay(ModalOverlay modalOverlay) {
        this.modalOverlay = modalOverlay;
    }
}
