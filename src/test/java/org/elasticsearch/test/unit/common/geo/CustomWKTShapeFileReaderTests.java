package org.elasticsearch.test.unit.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.geo.CustomWKTShapeFileReader;
import org.elasticsearch.common.geo.ShapeBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Tests for {@link org.elasticsearch.common.geo.CustomWKTShapeFileReader}
 */
public class CustomWKTShapeFileReaderTests {

    @Test
    public void testReadFiles() throws IOException {
        CustomWKTShapeFileReader reader = new CustomWKTShapeFileReader(ImmutableSettings.settingsBuilder().build());
        File shapeDirectory = new File(getClass().getResource("/org/elasticsearch/test/unit/common/geo/wkt").getPath());
        Map<String, Shape> shapesByName = reader.readFiles(shapeDirectory);

        Shape expected = ShapeBuilder.newRectangle()
                .topLeft(-176.848755, -34.414718)
                .bottomRight(178.841063, -52.578055)
                .build();

        assertEquals(shapesByName.get("New Zealand"), expected);
    }
}
