package io.abc_def.kickstart_fx.page;

import atlantafx.sampler.page.showcase.filemanager.FileManagerPage;
import io.abc_def.kickstart_fx.comp.SimpleRegionBuilder;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class FileBrowserPageComp extends SimpleRegionBuilder {

    @Override
    protected Region createSimple() {
        var atlantaFileManager = new FileManagerPage();
        VBox vbox = (VBox) atlantaFileManager.getSnapshotTarget();
        return (Region) ((BorderPane) vbox.getChildren().getFirst()).getCenter();
    }
}
