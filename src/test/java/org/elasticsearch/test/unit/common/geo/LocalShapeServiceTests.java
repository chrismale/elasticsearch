package org.elasticsearch.test.unit.common.geo;

import org.elasticsearch.common.geo.ShapeModule;
import org.elasticsearch.common.geo.ShapeService;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.testng.annotations.Test;

public class LocalShapeServiceTests {

    @Test
    public void testLocalShapeService() {
        Settings settings = ImmutableSettings.settingsBuilder().build();
        Injector injector = new ModulesBuilder().add(
                new SettingsModule(settings),
                new ShapeModule()).createInjector();

        ShapeService shapeService = injector.getInstance(ShapeService.class);
    }
}
