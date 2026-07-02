package tech.rawden.ara.util;

import java.util.ServiceLoader;
import java.util.function.Consumer;

public class ModuleLayerLoader {

    public static void loadAll(ModuleLayer layer, Consumer<Throwable> errorHandler) {
        ServiceLoader.load(layer, ModuleLayerLoader.class).forEach(loader -> {
            try {
                loader.load();
            } catch (Throwable t) {
                errorHandler.accept(t);
            }
        });
    }

    public void load() {}
}
