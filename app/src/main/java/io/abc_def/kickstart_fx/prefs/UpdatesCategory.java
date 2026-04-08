package io.abc_def.kickstart_fx.prefs;

import io.abc_def.kickstart_fx.platform.LabelGraphic;
import io.abc_def.kickstart_fx.platform.OptionsBuilder;

import org.int4.fx.builders.common.AbstractRegionBuilder;

public class UpdatesCategory extends AppPrefsCategory {

    @Override
    public String getId() {
        return "updates";
    }

    @Override
    protected LabelGraphic getIcon() {
        return new LabelGraphic.IconGraphic("mdi2d-download-box-outline");
    }

    public AbstractRegionBuilder<?, ?> create() {
        var prefs = AppPrefs.get();
        var builder = new OptionsBuilder();
        builder.addTitle("updates")
                .sub(new OptionsBuilder()
                        .pref(prefs.automaticallyCheckForUpdates)
                        .addToggle(prefs.automaticallyCheckForUpdates));
        return builder.buildComp();
    }
}
