package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionBuilder;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

import org.int4.fx.builders.common.AbstractRegionBuilder;

public class ScrollComp extends RegionBuilder<ScrollPane> {

    private final AbstractRegionBuilder<?, ?> content;

    public ScrollComp(AbstractRegionBuilder<?, ?> content) {
        this.content = content;
    }

    @Override
    public ScrollPane createSimple() {
        var r = content.build();
        var stack = new StackPane(r);
        stack.getStyleClass().add("scroll-comp-content");

        var sp = new ScrollPane(stack);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-comp");
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }
}
