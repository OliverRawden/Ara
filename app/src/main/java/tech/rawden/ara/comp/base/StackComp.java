package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionBuilder;

import javafx.scene.layout.StackPane;

import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.List;

public class StackComp extends RegionBuilder<StackPane> {

    private final List<? extends AbstractRegionBuilder<?, ?>> children;

    public StackComp(List<? extends AbstractRegionBuilder<?, ?>> children) {
        this.children = children;
    }

    @Override
    public StackPane createSimple() {
        var pane = new StackPane();
        for (var child : children) {
            pane.getChildren().add(child.build());
        }
        return pane;
    }
}
