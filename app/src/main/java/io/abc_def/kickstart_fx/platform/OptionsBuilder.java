package io.abc_def.kickstart_fx.platform;

import io.abc_def.kickstart_fx.comp.BaseRegionBuilder;

import io.abc_def.kickstart_fx.comp.RegionBuilder;
import io.abc_def.kickstart_fx.comp.base.LabelComp;
import io.abc_def.kickstart_fx.comp.base.OptionsComp;
import io.abc_def.kickstart_fx.comp.base.ToggleSwitchComp;
import io.abc_def.kickstart_fx.core.AppI18n;
import io.abc_def.kickstart_fx.prefs.AppPrefs;

import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;

import atlantafx.base.controls.Spacer;
import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.ArrayList;
import java.util.List;

public class OptionsBuilder {

    private final List<OptionsComp.Entry> entries = new ArrayList<>();
    private final List<Property<?>> props = new ArrayList<>();

    private ObservableValue<String> name;
    private ObservableValue<String> description;
    private AbstractRegionBuilder<?, ?> comp;
    private AbstractRegionBuilder<?, ?> lastCompHeadReference;
    private ObservableValue<String> lastNameReference;

    private void finishCurrent() {
        if (comp == null) {
            return;
        }

        var entry = new OptionsComp.Entry(null, description, name, comp);
        description = null;
        name = null;
        lastNameReference = null;
        comp = null;
        lastCompHeadReference = null;
        entries.add(entry);
    }

    public OptionsBuilder sub(OptionsBuilder builder) {
        props.addAll(builder.props);
        var c = builder.lastCompHeadReference;
        var n = builder.lastNameReference;
        pushComp(builder.buildComp());
        if (c != null) {
            lastCompHeadReference = c;
        }
        if (n != null) {
            lastNameReference = n;
        }
        return this;
    }

    public OptionsBuilder addTitle(String titleKey) {
        finishCurrent();
        entries.add(new OptionsComp.Entry(
                titleKey, null, null, new LabelComp(AppI18n.observable(titleKey)).style("title-header")));
        return this;
    }

    public OptionsBuilder pref(Object property) {
        var mapping = AppPrefs.get().getMapping(property);
        pref(mapping.getKey(), mapping.isRequiresRestart());
        return this;
    }

    public OptionsBuilder pref(String key, boolean requiresRestart) {
        name(key);
        if (requiresRestart) {
            description(AppI18n.observable(key + "Description").map(s -> s + "\n\n" + AppI18n.get("requiresRestart")));
        } else {
            description(AppI18n.observable(key + "Description"));
        }
        return this;
    }

    public OptionsBuilder hide(ObservableValue<Boolean> b) {
        lastCompHeadReference.invisible(b);
        return this;
    }

    private void pushComp(AbstractRegionBuilder<?, ?> comp) {
        finishCurrent();
        this.comp = comp;
        this.lastCompHeadReference = comp;
    }

    public OptionsBuilder addToggle(Property<Boolean> prop) {
        var comp = new ToggleSwitchComp(prop, null, null);
        pushComp(comp);
        props.add(prop);
        return this;
    }

    public OptionsBuilder spacer(double size) {
        return addComp(RegionBuilder.vspacer(size));
    }

    public OptionsBuilder name(String nameKey) {
        finishCurrent();
        name = AppI18n.observable(nameKey);
        lastNameReference = name;
        return this;
    }

    public OptionsBuilder description(String descriptionKey) {
        finishCurrent();
        description = AppI18n.observable(descriptionKey);
        return this;
    }

    public OptionsBuilder description(ObservableValue<String> description) {
        finishCurrent();
        this.description = description;
        return this;
    }

    public OptionsBuilder addComp(AbstractRegionBuilder<?, ?> comp) {
        pushComp(comp);
        return this;
    }

    public OptionsBuilder addComp(AbstractRegionBuilder<?, ?> comp, Property<?> prop) {
        pushComp(comp);
        props.add(prop);
        return this;
    }

    public OptionsComp buildComp() {
        finishCurrent();
        return new OptionsComp(entries);
    }
}
