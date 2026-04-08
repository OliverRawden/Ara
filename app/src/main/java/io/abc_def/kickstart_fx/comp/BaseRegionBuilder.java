package io.abc_def.kickstart_fx.comp;

import io.abc_def.kickstart_fx.platform.BindingsHelper;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;

import org.int4.fx.builders.common.AbstractRegionBuilder;

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
