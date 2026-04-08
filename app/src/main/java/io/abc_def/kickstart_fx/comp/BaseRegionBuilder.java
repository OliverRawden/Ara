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

    public B padding(Insets insets) {
        return apply(struc -> struc.setPadding(insets));
    }
}
