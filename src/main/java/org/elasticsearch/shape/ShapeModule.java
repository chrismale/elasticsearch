package org.elasticsearch.shape;

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
        bind(ShapeService.class).asEagerSingleton();
    }
}
