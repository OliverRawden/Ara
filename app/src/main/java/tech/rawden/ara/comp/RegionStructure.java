package tech.rawden.ara.comp;

import javafx.scene.layout.Region;

public interface RegionStructure<T extends Region> {
    T get();
}
