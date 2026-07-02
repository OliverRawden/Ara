package tech.rawden.ara.comp;

import javafx.scene.layout.Region;

import org.int4.fx.builders.common.AbstractRegionBuilder;
import org.int4.fx.builders.context.BuildContext;

import java.util.function.Supplier;

public abstract class RegionBuilder<T extends Region> extends AbstractRegionBuilder<T, RegionBuilder<T>> {

    public static RegionBuilder<Region> empty() {
        return of(() -> {
            var r = new Region();
            r.getStyleClass().add("empty");
            return r;
        });
    }

    public static <R extends Region> RegionBuilder<R> of(Supplier<R> r) {
        return new RegionBuilder<>() {
            @Override
            protected R createSimple() {
                return r.get();
            }
        };
    }

    @Override
    public final T build(BuildContext context) {
        var r = createSimple();
        initialize(r);
        return r;
    }

    protected abstract T createSimple();
}
