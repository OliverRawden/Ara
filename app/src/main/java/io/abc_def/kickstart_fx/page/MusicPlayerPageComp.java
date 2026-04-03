package io.abc_def.kickstart_fx.page;

import io.abc_def.kickstart_fx.comp.SimpleRegionBuilder;
import javafx.scene.layout.Region;

import atlantafx.sampler.page.showcase.musicplayer.MusicPlayerPage;

public class MusicPlayerPageComp extends SimpleRegionBuilder {

    @Override
    protected Region createSimple() {
        var atlantaMusicPlayer = new MusicPlayerPage();
        return atlantaMusicPlayer;
    }
}
