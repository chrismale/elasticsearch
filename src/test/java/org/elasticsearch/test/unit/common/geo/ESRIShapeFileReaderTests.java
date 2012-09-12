package org.elasticsearch.test.unit.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.geo.ESRIShapeFileReader;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests for {@link ESRIShapeFileReader}
 */
public class ESRIShapeFileReaderTests {

    @Test
    public void testReadFiles() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(
                new File(getClass().getResource("/org/elasticsearch/test/unit/common/geo/esri/test.shp").getFile()));
        ByteBuffer shpBuffer = ByteBuffer.allocate(2048000);
        fileInputStream.getChannel().read(shpBuffer);
        shpBuffer.flip();

        InputStream dbfInputStream = new FileInputStream(
                new File(getClass().getResource("/org/elasticsearch/test/unit/common/geo/esri/test.dbf").getFile()));

        List<Shape> shapes = ESRIShapeFileReader.parseShpFile(shpBuffer);
        List<Map<String, Object>> shapeMetadata = ESRIShapeFileReader.parseDBFFile(dbfInputStream);

        assertEquals(shapes.size(), 177);
        assertEquals(shapeMetadata.size(), 177);

        dbfInputStream.close();
        fileInputStream.close();
    }
}
