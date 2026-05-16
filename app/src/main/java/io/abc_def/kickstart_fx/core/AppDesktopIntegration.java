package io.abc_def.kickstart_fx.core;

import io.abc_def.kickstart_fx.core.mode.AppOperationMode;
import io.abc_def.kickstart_fx.issue.ErrorEventFactory;
import io.abc_def.kickstart_fx.platform.PlatformState;
import io.abc_def.kickstart_fx.util.OsType;
import io.abc_def.kickstart_fx.util.ThreadHelper;

import java.awt.*;
import java.awt.desktop.*;
import java.util.List;

public class AppDesktopIntegration {

    public static void init() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().addAppEventListener(new SystemSleepListener() {
                    @Override
                    public void systemAboutToSleep(SystemSleepEvent e) {}

                    @Override
                    public void systemAwoke(SystemSleepEvent e) {

                        // TODO: Do we want to do something on hibernation?

                        // If we run this at the same time as the system is sleeping, there might be exceptions
                        // because the platform does not like being shut down while sleeping
                        // This assures that it will be run later, on system wake
                        //                        ThreadHelper.runAsync(() -> {
                        //                            ThreadHelper.sleep(1000);
                        //                            AppOperationMode.close();
                        //                        });
                    }
                });
            }

            // This will initialize the toolkit on macOS and create the dock icon
            // macOS does not like applications that run fully in the background, so always do it
            if (OsType.ofLocal() == OsType.MACOS && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().setPreferencesHandler(e -> {
                    if (PlatformState.getCurrent() != PlatformState.RUNNING) {
                        return;
                    }

                    if (AppLayoutModel.get() != null) {
                        AppLayoutModel.get().selectSettings();
                    }
                });

                // URL open operations have to be handled in a special way on macOS!
                Desktop.getDesktop().setOpenURIHandler(e -> {
                    AppOpenArguments.handle(List.of(e.getURI().toString()));
                });

                Desktop.getDesktop().addAppEventListener(new AppReopenedListener() {
                    @Override
                    public void appReopened(AppReopenedEvent e) {
                        AppOperationMode.switchToAsync(AppOperationMode.GUI);
                    }
                });

                Desktop.getDesktop().setQuitHandler((e, response) -> {
                    response.cancelQuit();
                    ThreadHelper.runAsync(() -> {
                        AppOperationMode.externalShutdown();
                    });
                });
            }
        } catch (Throwable ex) {
            ErrorEventFactory.fromThrowable(ex).term().handle();
        }
    }
}
