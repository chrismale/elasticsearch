package org.elasticsearch.common.geo;

import org.elasticsearch.common.inject.AbstractModule;

public class ShapeModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ShapeService.class).to(LocalShapeService.class).asEagerSingleton();
    }
}
