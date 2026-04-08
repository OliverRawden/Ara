package io.abc_def.kickstart_fx.prefs;


import io.abc_def.kickstart_fx.comp.BaseRegionBuilder;
import io.abc_def.kickstart_fx.comp.RegionBuilder;
import io.abc_def.kickstart_fx.comp.base.LabelComp;
import io.abc_def.kickstart_fx.comp.base.PrettyImageHelper;
import io.abc_def.kickstart_fx.comp.base.VerticalComp;
import io.abc_def.kickstart_fx.core.AppFontSizes;
import io.abc_def.kickstart_fx.core.AppNames;
import io.abc_def.kickstart_fx.core.AppProperties;
import io.abc_def.kickstart_fx.platform.LabelGraphic;
import io.abc_def.kickstart_fx.platform.OptionsBuilder;
import io.abc_def.kickstart_fx.update.AppDistributionType;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import atlantafx.base.controls.Spacer;
import atlantafx.base.theme.Styles;
import org.int4.fx.builders.common.AbstractRegionBuilder;

import java.util.List;

public class AboutCategory extends AppPrefsCategory {

    @Override
    public String getId() {
        return "about";
    }

    @Override
    protected LabelGraphic getIcon() {
        return new LabelGraphic.IconGraphic("mdi2i-information-outline");
    }

    @Override
    public AbstractRegionBuilder<?, ?> create() {
        var props = createProperties();
        var update = new UpdateCheckComp().prefWidth(600);
        return new VerticalComp(List.of(
                        props,
                        RegionBuilder.vspacer(1),
                        update,
                        RegionBuilder.vspacer(5),
                        RegionBuilder.hseparator().apply(r -> r.setPadding(Insets.EMPTY)).maxWidth(600)))
                .apply(s -> s.setFillWidth(true))
                .apply(struc -> struc.setSpacing(12))
                .style("information")
                .style("about-tab");
    }

    private AbstractRegionBuilder<?, ?> createProperties() {
        var title = RegionBuilder.of(() -> {
            var header = new Label();
            header.setText(AppNames.ofCurrent().getName() + " Desktop");
            AppFontSizes.xl(header);
            var desc = new Label();
            desc.setText("Version " + AppProperties.get().getVersion() + " ("
                    + AppProperties.get().getArch() + ")");
            AppFontSizes.xs(desc);
            var text = new VBox(header, new Spacer(), desc);
            text.setAlignment(Pos.CENTER_LEFT);

            var size = 40;
            var graphic = PrettyImageHelper.ofFixedSizeSquare("icon/logo_40x40.png", size)
                    .build();

            var hbox = new HBox(graphic, text);
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setFillHeight(true);
            hbox.setSpacing(10);
            return hbox;
        });

        title.style(Styles.TEXT_BOLD);

        var section = new OptionsBuilder()
                .addComp(RegionBuilder.vspacer(40))
                .addComp(title, null)
                .addComp(RegionBuilder.vspacer(10))
                .name("build")
                .addComp(new LabelComp(AppProperties.get().getBuild()), null)
                .name("distribution")
                .addComp(new LabelComp(AppDistributionType.get().toTranslatedString()))
                .name("virtualMachine")
                .addComp(
                        new LabelComp(System.getProperty("java.vm.vendor") + " "
                                + System.getProperty("java.vm.name")
                                + " "
                                + System.getProperty("java.vm.version")),
                        null)
                .name("javafxBuild")
                .addComp(new LabelComp("JavaFX " + System.getProperty("javafx.runtime.version") + (Boolean.getBoolean("javafx.enablePreview") ? " + Preview" : "")))
                .buildComp();
        return section.style("properties-comp");
    }
}
