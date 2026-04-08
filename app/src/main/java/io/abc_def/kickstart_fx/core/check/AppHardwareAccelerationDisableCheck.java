package io.abc_def.kickstart_fx.core.check;


import io.abc_def.kickstart_fx.core.AppCache;
import io.abc_def.kickstart_fx.issue.ErrorEventFactory;
import io.abc_def.kickstart_fx.util.OsType;

public class AppHardwareAccelerationDisableCheck {

    public static void check() {
        if (OsType.ofLocal() != OsType.LINUX) {
            return;
        }

        var cached = AppCache.getBoolean("hardwareAccelerationDisabled", false);
        if (!cached) {
            return;
        }

        AppCache.clear("hardwareAccelerationDisabled");

        ErrorEventFactory.fromMessage(
                        "A graphics driver issue was detected and the application has been restarted. Hardware acceleration has been disabled.")
                .expected()
                .handle();
    }
}
