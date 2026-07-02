package tech.rawden.ara.core;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.paint.Color;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;

import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

public class AraTheme {

    private static final Logger LOG = Logger.getLogger(AraTheme.class.getName());
    private static final BooleanProperty darkMode = new SimpleBooleanProperty(false);
    private static boolean useSystemAccent = true;
    private static String currentStylesheetURL;
    private static Runnable onStyleChanged;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        setDark(false);
        try {
            Platform.getPreferences().accentColorProperty().addListener((obs, o, n) -> {
                rebuild();
            });
        } catch (Exception e) {
            LOG.warning("Platform accent color API unavailable: " + e.getMessage());
        }
    }

    public static void setOnStyleChanged(Runnable r) {
        onStyleChanged = r;
    }

    public static BooleanProperty darkModeProperty() {
        return darkMode;
    }

    public static boolean isDark() {
        return darkMode.get();
    }

    public static void setDark(boolean dark) {
        darkMode.set(dark);
        if (dark) {
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheetBSS());
        } else {
            Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheetBSS());
        }
        rebuild();
    }

    public static void setUseSystemAccent(boolean use) {
        useSystemAccent = use;
        rebuild();
    }

    public static boolean isUseSystemAccent() {
        return useSystemAccent;
    }

    public static String getAccentStylesheetURL() {
        return currentStylesheetURL;
    }

    private static void rebuild() {
        if (useSystemAccent) {
            try {
                writeAccentStylesheet();
            } catch (Exception e) {
                LOG.warning("Failed to write accent stylesheet: " + e.getMessage());
                currentStylesheetURL = null;
            }
        } else {
            currentStylesheetURL = null;
        }
        if (onStyleChanged != null) onStyleChanged.run();
    }

    private static void writeAccentStylesheet() throws IOException {
        var accent = getAccentColor();
        var dark = isDark();
        var fg = dark ? brighten(accent, 0.35) : darken(accent, 0.20);

        var css = String.format(
                """
                * {
                    -color-accent-emphasis: %s;
                    -color-accent-subtle: rgba(%d,%d,%d,0.15);
                    -color-accent-fg: %s;
                }
                """,
                toHex(accent),
                (int) (accent.getRed() * 255),
                (int) (accent.getGreen() * 255),
                (int) (accent.getBlue() * 255),
                toHex(fg));

        var path = Files.createTempFile("ara-accent-", ".css");
        path.toFile().deleteOnExit();
        Files.writeString(path, css);
        currentStylesheetURL = path.toUri().toURL().toExternalForm();
    }

    private static Color getAccentColor() {
        try {
            return Platform.getPreferences().getAccentColor();
        } catch (Exception e) {
            LOG.warning("Could not get system accent color, falling back to blue: " + e.getMessage());
            return Color.web("#007AFF");
        }
    }

    private static Color brighten(Color c, double amount) {
        return Color.hsb(c.getHue(), c.getSaturation(), Math.clamp(c.getBrightness() + amount, 0.0, 1.0));
    }

    private static Color darken(Color c, double amount) {
        return Color.hsb(c.getHue(), c.getSaturation(), Math.clamp(c.getBrightness() - amount, 0.0, 1.0));
    }

    private static String toHex(Color c) {
        return String.format(
                "#%02X%02X%02X", (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }
}
