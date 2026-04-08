package io.abc_def.kickstart_fx.comp.base;

import io.abc_def.kickstart_fx.comp.RegionBuilder;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;

import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.List;

public class HorizontalComp extends RegionBuilder<HBox> {

    private final ObservableList<AbstractRegionBuilder<?, ?>> entries;

    public HorizontalComp(List<AbstractRegionBuilder<?, ?>> comps) {
        entries = FXCollections.observableArrayList(List.copyOf(comps));
    }

    public RegionBuilder<HBox> spacing(double spacing) {
        return apply(struc -> struc.setSpacing(spacing));
    }

    @Override
    public HBox createSimple() {
        var b = new HBox();
        b.getStyleClass().add("horizontal-comp");
        entries.addListener((ListChangeListener<? super AbstractRegionBuilder<?, ?>>) c -> {
            b.getChildren().setAll(c.getList().stream().map(ab -> ab.build()).toList());
        });
        for (var entry : entries) {
            b.getChildren().add(entry.build());
        }
        b.setAlignment(Pos.CENTER);
        return b;
    }
}
