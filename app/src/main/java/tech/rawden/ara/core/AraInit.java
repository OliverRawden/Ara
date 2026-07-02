package tech.rawden.ara.core;

import tech.rawden.ara.ai.ModelManager;
import tech.rawden.ara.platform.PlatformThread;

import java.util.logging.Logger;

public class AraInit {

    private static final Logger LOG = Logger.getLogger(AraInit.class.getName());

    public static void init(String[] args) {
        try {
            LOG.info("Starting Ara...");
            AraModel.init();
            var mm = new ModelManager();
            mm.ensureModelsDirectory();

            PlatformThread.runLaterIfNeededBlocking(() -> {
                var stage = new javafx.stage.Stage();
                var scene = new javafx.scene.Scene(new javafx.scene.layout.StackPane(), 1100, 720);
                stage.setScene(scene);
                stage.setTitle("Ara");
                stage.show();
            });

            LOG.info("Ara initialized successfully");
        } catch (Throwable t) {
            LOG.severe("Startup failed: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
