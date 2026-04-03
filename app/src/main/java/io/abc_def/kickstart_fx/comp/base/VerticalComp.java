package io.abc_def.kickstart_fx.comp.base;

import io.abc_def.kickstart_fx.comp.RegionBuilder;
import io.abc_def.kickstart_fx.platform.PlatformThread;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.layout.VBox;
import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.List;

public class VerticalComp extends RegionBuilder<VBox> {

    private final ObservableList<? extends AbstractRegionBuilder<?, ?>> entries;

    public VerticalComp(List<? extends AbstractRegionBuilder<?, ?>> comps) {
        entries = FXCollections.observableArrayList(List.copyOf(comps));
    }

    public RegionBuilder<VBox> spacing(double spacing) {
        return apply(struc -> struc.setSpacing(spacing));
    }

    @Override
    public VBox createSimple() {
        VBox b = new VBox();
        b.getStyleClass().add("vertical-comp");
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
