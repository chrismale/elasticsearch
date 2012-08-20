package org.elasticsearch.test.unit.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.geo.ShapeBuilder;
import org.elasticsearch.common.geo.ShapeModule;
import org.elasticsearch.common.geo.ShapeService;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Tests for {@link org.elasticsearch.common.geo.LocalShapeService}
 */
public class LocalShapeServiceTests {

    @Test
    public void testLocalShapeService() {
        String mockConfigPath = getClass().getResource("/org/elasticsearch/test/unit/common/geo").getPath();
        Settings settings = ImmutableSettings.settingsBuilder().put("path.home", mockConfigPath).build();
        Environment environment = new Environment(settings);
        Injector injector = new ModulesBuilder().add(
                new EnvironmentModule(environment),
                new SettingsModule(settings),
                new ShapeModule(settings)).createInjector();

        ShapeService shapeService = injector.getInstance(ShapeService.class);

        Shape expected = ShapeBuilder.newRectangle()
                .topLeft(-176.848755, -34.414718)
                .bottomRight(178.841063, -52.578055)
                .build();
        assertEquals(shapeService.shape("New Zealand"), expected);

        Shape bigRectangle = ShapeBuilder.newRectangle()
                .topLeft(-45, 45)
                .bottomRight(45, -45)
                .build();
        shapeService.add("Big Rectangle", bigRectangle);
        assertEquals(shapeService.shape("Big Rectangle"), bigRectangle);
    }
}
