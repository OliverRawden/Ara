package io.abc_def.kickstart_fx.update;

import io.abc_def.kickstart_fx.core.*;
import io.abc_def.kickstart_fx.issue.ErrorEventFactory;
import io.abc_def.kickstart_fx.issue.TrackEvent;
import io.abc_def.kickstart_fx.util.LocalExec;
import io.abc_def.kickstart_fx.util.OsType;
import io.abc_def.kickstart_fx.util.Translatable;

import javafx.beans.value.ObservableValue;

import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Supplier;

public enum AppDistributionType implements Translatable {
    UNKNOWN("unknown", () -> new BasicUpdater(false)),
    DEVELOPMENT("development", () -> new BasicUpdater(false)),
    PORTABLE("portable", () -> new BasicUpdater(true)),
    APP_IMAGE("appImage", () -> new BasicUpdater(true)),
    NATIVE_INSTALLATION("install", () -> new BasicUpdater(true));

    private static AppDistributionType type;

    @Getter
    private final String id;

    private final Supplier<UpdateHandler> updateHandlerSupplier;
    private UpdateHandler updateHandler;

    AppDistributionType(String id, Supplier<UpdateHandler> updateHandlerSupplier) {
        this.id = id;
        this.updateHandlerSupplier = updateHandlerSupplier;
    }

    public static void init() {
        if (type != null) {
            return;
        }

        if (!AppProperties.get().isRuntimeImage()) {
            type = DEVELOPMENT;
            return;
        }

        if (!AppProperties.get().isNewBuildSession() && !isDifferentDaemonExecutable()) {
            var cached = AppCache.getNonNull("dist", String.class, () -> null);
            var cachedType = Arrays.stream(values())
                    .filter(d -> d.getId().equals(cached))
                    .findAny()
                    .orElse(null);
            if (cachedType != null) {
                type = cachedType;
                return;
            }
        }

        var det = determine();

        // Don't cache unknown type
        if (det == UNKNOWN) {
            return;
        }

        type = det;
        AppCache.update("dist", type.getId());
        TrackEvent.withInfo("Determined distribution type")
                .tag("type", type.getId())
                .handle();
    }

    private static boolean isDifferentDaemonExecutable() {
        var cached = AppCache.getNonNull("daemonExecutable", String.class, () -> null);
        var current = AppInstallation.ofCurrent().getExecutablePath().toString();
        if (current.equals(cached)) {
            return false;
        }

        AppCache.update("daemonExecutable", current);
        return true;
    }

    public static AppDistributionType get() {
        if (type == null) {
            return UNKNOWN;
        }

        return type;
    }

    public static AppDistributionType determine() {
        var base = AppInstallation.ofCurrent().getBaseInstallationPath();
        if (OsType.ofLocal() == OsType.MACOS) {
            if (!base.equals(AppInstallation.ofDefault().getBaseInstallationPath())) {
                return PORTABLE;
            }

            try {
                var r = LocalExec.readStdoutIfPossible(
                        "pkgutil",
                        "--pkg-info",
                        AppNames.ofCurrent().getGroupName() + "."
                                + AppNames.ofCurrent().getKebapName());
                if (r.isEmpty()) {
                    return PORTABLE;
                }
            } catch (Exception ex) {
                ErrorEventFactory.fromThrowable(ex).omit().handle();
                return PORTABLE;
            }
        } else {
            var file = base.resolve("installation");
            if (!Files.exists(file)) {
                return PORTABLE;
            }
        }

        // Fix for community AUR builds that use the RPM dist
        if (OsType.ofLocal() == OsType.LINUX && Files.exists(Path.of("/etc/arch-release"))) {
            return PORTABLE;
        }

        if (OsType.ofLocal() == OsType.LINUX
                && System.getenv("APPDIR") != null
                && System.getenv("APPIMAGE") != null) {
            try {
                var dir = Path.of(System.getenv("APPDIR"));
                if (AppInstallation.ofCurrent()
                        .getBaseInstallationPath()
                        .startsWith(dir)) {
                    return APP_IMAGE;
                }

            } catch (InvalidPathException ignored) {
            }
        }

        return AppDistributionType.NATIVE_INSTALLATION;
    }

    public UpdateHandler getUpdateHandler() {
        if (updateHandler == null) {
            updateHandler = updateHandlerSupplier.get();
        }
        return updateHandler;
    }

    @Override
    public ObservableValue<String> toTranslatedString() {
        return AppI18n.observable(getId() + "Dist");
    }
}
