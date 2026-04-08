package io.abc_def.kickstart_fx.prefs;

import io.abc_def.kickstart_fx.platform.LabelGraphic;

import org.int4.fx.builders.common.AbstractRegionBuilder;

public abstract class AppPrefsCategory {

    public boolean show() {
        return true;
    }

    public abstract String getId();

    protected abstract LabelGraphic getIcon();

    public abstract AbstractRegionBuilder<?, ?> create();
}
