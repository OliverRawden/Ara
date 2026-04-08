package io.abc_def.kickstart_fx.page;

import io.abc_def.kickstart_fx.comp.SimpleRegionBuilder;

import javafx.scene.layout.Region;

import atlantafx.sampler.page.showcase.BlueprintsPage;

public class BlueprintsPageComp extends SimpleRegionBuilder {

    @Override
    protected Region createSimple() {
        var atlantaBlueprints = new BlueprintsPage();
        return atlantaBlueprints;
    }
}
