package io.abc_def.kickstart_fx.comp;

import io.abc_def.kickstart_fx.platform.BindingsHelper;
import io.abc_def.kickstart_fx.platform.PlatformThread;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.function.Consumer;

public abstract class BaseRegionBuilder<T extends Region, B extends BaseRegionBuilder<T, B>>
        extends AbstractRegionBuilder<T, B> {

    public BaseRegionBuilder() {
        apply(t -> {
            BindingsHelper.preserve(t, BaseRegionBuilder.this);
        });
    }

    public B hgrow() {
        apply(t -> HBox.setHgrow(t, Priority.ALWAYS));
        return self();
    }

    public B vgrow() {
        apply(t -> VBox.setVgrow(t, Priority.ALWAYS));
        return self();
    }

    public B padding(Insets insets) {
        return apply(struc -> struc.setPadding(insets));
    }

    public B show(ObservableValue<Boolean> when) {
        return this.hide(when.map((b) -> !b).orElse(true));
    }

    public B hide(ObservableValue<Boolean> o) {
        return apply(struc -> {
            var region = struc;
            BindingsHelper.preserve(region, o);
            o.subscribe(n -> {
                PlatformThread.runLaterIfNeeded(() -> {
                    if (!n) {
                        region.setVisible(true);
                        region.setManaged(true);
                    } else {
                        region.setVisible(false);
                        region.setManaged(false);
                    }
                });
            });
        });
    }
}
