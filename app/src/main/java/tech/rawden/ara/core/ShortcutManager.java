package tech.rawden.ara.core;

import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;

public class ShortcutManager {

    public interface Actions {
        void newChat();

        void closeChat();

        void quitApp();

        void toggleSettings();
    }

    public ShortcutManager(Scene scene, Actions actions) {
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+N"), actions::newChat);
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+W"), actions::closeChat);
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+Q"), actions::quitApp);
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+,"), actions::toggleSettings);
    }
}
