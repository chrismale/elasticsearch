package org.elasticsearch.common.geo;

import org.elasticsearch.common.inject.AbstractModule;

/**
 * Module {@link ShapeService}
 */
public class ShapeModule extends AbstractModule {

    @Override
    // TODO: Should we accept Settings?
    protected void configure() {
        // TODO: Need to allow other implementations to be chosen once we have them
        bind(ShapeService.class).to(LocalShapeService.class).asEagerSingleton();
    }
}
