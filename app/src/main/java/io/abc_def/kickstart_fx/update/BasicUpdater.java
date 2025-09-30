package io.abc_def.kickstart_fx.update;

import io.abc_def.kickstart_fx.comp.base.ModalButton;
import io.abc_def.kickstart_fx.core.*;
import io.abc_def.kickstart_fx.core.mode.AppOperationMode;
import io.abc_def.kickstart_fx.util.Hyperlinks;
import io.abc_def.kickstart_fx.util.LocalExec;
import io.abc_def.kickstart_fx.util.OsType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BasicUpdater extends UpdateHandler {

    public BasicUpdater(boolean thread) {
        super(thread);
    }

    @Override
    public List<ModalButton> createActions() {
        var list = new ArrayList<ModalButton>();
        list.add(new ModalButton("ignore", null, true, false));
        list.add(new ModalButton(
                "checkOutUpdate",
                () -> {
                    var rel = getLastUpdateCheckResult().getValue();
                    if (rel == null || !rel.isUpdate()) {
                        return;
                    }

                    Hyperlinks.open(rel.getReleaseUrl());
                },
                false,
                true));

        // On Windows, we can implement a simple autoupdater
        // This is however very basic
        if (OsType.ofLocal() == OsType.WINDOWS && (AppDistributionType.get() == AppDistributionType.NATIVE_INSTALLATION || !AppProperties.get().isRuntimeImage())) {
            list.add(new ModalButton(
                    "installUpdate",
                    () -> {
                        var rel = getLastUpdateCheckResult().getValue();
                        if (rel == null || !rel.isUpdate()) {
                            return;
                        }

                        var url = rel.getRepository() + "/releases/download/" + rel.getVersion() + "/" +
                                AppNames.ofCurrent().getDistName() + "-installer-windows-" + AppProperties.get().getArch() + ".msi";
                        AppOperationMode.executeAfterShutdown(() -> {
                            var command = "start \"\" /wait msiexec /i \"" + url + "\" /qb&start \"\" \"" + AppInstallation.ofCurrent().getExecutablePath() + "\"";
                            LocalExec.executeAsync("cmd", "/c", command);
                        });
                    },
                    false,
                    true));
        }
        return list;
    }

    private boolean isUpdate(String releaseVersion) {
        if (!AppProperties.get().getVersion().equals(releaseVersion)) {
            event("Release has a different version");
            return true;
        }

        return false;
    }

    public synchronized AvailableRelease refreshUpdateCheckImpl() throws Exception {
        var found = AppReleases.getMarkedLatestRelease();
        if (found.isEmpty()) {
            return null;
        }

        var rel = found.get();
        event("Determined latest suitable release " + rel.getTagName());
        var isUpdate = isUpdate(rel.getTagName());
        lastUpdateCheckResult.setValue(new AvailableRelease(
                AppProperties.get().getVersion(),
                AppDistributionType.get().getId(),
                rel.getTagName(),
                rel.getHtmlUrl().toString(),
                rel.getOwner().getHtmlUrl().toString(),
                "## Changes in v" + rel.getTagName() + "\n\n" + rel.getBody(),
                Instant.now(),
                isUpdate));
        return lastUpdateCheckResult.getValue();
    }
}
