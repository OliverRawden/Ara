package tech.rawden.ara.core;

import tech.rawden.ara.Main;

import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

public class MacMenuBar {

    public static MenuBar create(ShortcutManager.Actions actions) {
        var menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);

        var appMenu = new Menu("");

        var aboutItem = new MenuItem("About Ara");
        aboutItem.setOnAction(e -> {
            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About Ara");
            alert.setHeaderText("Ara v" + Main.VERSION);
            alert.setContentText("Private AI assistant.\nYour conversations stay on this device.");
            alert.show();
        });

        var settingsItem = new MenuItem("Settings\u2026");
        settingsItem.setAccelerator(KeyCombination.valueOf("Shortcut+,"));
        settingsItem.setOnAction(e -> actions.toggleSettings());

        var quitItem = new MenuItem("Quit Ara");
        quitItem.setAccelerator(KeyCombination.valueOf("Shortcut+Q"));
        quitItem.setOnAction(e -> actions.quitApp());

        appMenu.getItems().addAll(aboutItem, new SeparatorMenuItem(), settingsItem, new SeparatorMenuItem(), quitItem);

        var fileMenu = new Menu("File");

        var newItem = new MenuItem("New Chat");
        newItem.setAccelerator(KeyCombination.valueOf("Shortcut+N"));
        newItem.setOnAction(e -> actions.newChat());

        var closeItem = new MenuItem("Close Chat");
        closeItem.setAccelerator(KeyCombination.valueOf("Shortcut+W"));
        closeItem.setOnAction(e -> actions.closeChat());

        fileMenu.getItems().addAll(newItem, new SeparatorMenuItem(), closeItem);

        var editMenu = new Menu("Edit");

        editMenu.getItems()
                .addAll(
                        standardItem("Undo", "Shortcut+Z"),
                        standardItem("Redo", "Shortcut+Shift+Z"),
                        new SeparatorMenuItem(),
                        standardItem("Cut", "Shortcut+X"),
                        standardItem("Copy", "Shortcut+C"),
                        standardItem("Paste", "Shortcut+V"),
                        new SeparatorMenuItem(),
                        standardItem("Select All", "Shortcut+A"));

        var windowMenu = new Menu("Window");

        var minimizeItem = new MenuItem("Minimize");
        minimizeItem.setAccelerator(KeyCombination.valueOf("Shortcut+M"));
        minimizeItem.setOnAction(e -> {
            var w = menuBar.getScene().getWindow();
            if (w instanceof Stage s) {
                s.setIconified(true);
            }
        });

        windowMenu.getItems().add(minimizeItem);

        menuBar.getMenus().addAll(appMenu, fileMenu, editMenu, windowMenu);
        return menuBar;
    }

    private static MenuItem standardItem(String title, String shortcut) {
        var item = new MenuItem(title);
        item.setAccelerator(KeyCombination.valueOf(shortcut));
        return item;
    }
}
