package tech.rawden.ara.comp.base;

import tech.rawden.ara.comp.RegionBuilder;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.HBox;

import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.List;

public class HorizontalComp extends RegionBuilder<HBox> {

    private final ObservableList<? extends AbstractRegionBuilder<?, ?>> entries;

    public HorizontalComp(List<? extends AbstractRegionBuilder<?, ?>> comps) {
        entries = FXCollections.observableArrayList(List.copyOf(comps));
    }

    @Override
    public HBox createSimple() {
        HBox b = new HBox();
        b.getStyleClass().add("horizontal-comp");
        entries.addListener((ListChangeListener<? super AbstractRegionBuilder<?, ?>>) c -> {
            b.getChildren()
                    .setAll(c.getList().stream()
                            .map(AbstractRegionBuilder::build)
                            .toList());
        });
        for (var entry : entries) {
            b.getChildren().add(entry.build());
        }
        return b;
    }
}
