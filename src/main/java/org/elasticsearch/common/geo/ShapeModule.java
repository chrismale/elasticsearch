package org.elasticsearch.common.geo;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;

/**
 * Module {@link ShapeService}
 */
public class ShapeModule extends AbstractModule {

    private final Settings settings;

    public ShapeModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        // TODO: Need to allow other implementations to be chosen once we have them
        bind(ShapeService.class).to(LocalShapeService.class).asEagerSingleton();
    }
}
