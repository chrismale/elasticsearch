package org.elasticsearch.test.unit.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.geo.ESRIShapeFileReader;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests for {@link ESRIShapeFileReader}
 */
public class ESRIShapeFileReaderTests {

    @Test
    public void testReadFiles() throws IOException {
        ESRIShapeFileReader reader = new ESRIShapeFileReader(ImmutableSettings.settingsBuilder().build());
        File shapeDirectory = new File(getClass().getResource("/org/elasticsearch/test/unit/common/geo/esri").getPath());
        Map<String, Shape> shapesByName = reader.readFiles(shapeDirectory);

        assertEquals(shapesByName.size(), 177);
        assertNotNull(shapesByName.get("New Zealand"));
    }
}
