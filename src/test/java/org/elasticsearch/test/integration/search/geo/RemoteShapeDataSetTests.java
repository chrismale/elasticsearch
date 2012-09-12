package org.elasticsearch.test.integration.search.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.geo.RemoteESRIShapeDataSet;
import org.elasticsearch.common.geo.ShapeDataSet;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class RemoteShapeDataSetTests {

    @Test
    public void testShapeData() throws IOException {
        String filePath = RemoteShapeDataSetTests.class.getResource(
                "/org/elasticsearch/test/integration/search/geo/test.zip").getFile();
        RemoteESRIShapeDataSet testDataSet = new RemoteESRIShapeDataSet(
                "test_data_set", "file://" + filePath, "name");

        List<Map<String, Object>> shapeData = testDataSet.shapeData();
        assertEquals(shapeData.size(), 177);

        Shape shape = (Shape) shapeData.get(0).get("shape");
        assertNotNull(shape);
    }
}
