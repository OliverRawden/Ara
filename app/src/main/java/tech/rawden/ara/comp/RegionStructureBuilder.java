package tech.rawden.ara.comp;

public abstract class RegionStructureBuilder<T extends javafx.scene.layout.Region, S extends RegionStructure<T>>
        extends RegionBuilder<T> {

    @Override
    protected final T createSimple() {
        return createBase().get();
    }

    protected abstract S createBase();
}
