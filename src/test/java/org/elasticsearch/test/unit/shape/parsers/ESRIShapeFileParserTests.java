package org.elasticsearch.test.unit.shape.parsers;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.shape.parsers.ESRIShapeFileParser;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests for {@link org.elasticsearch.shape.parsers.ESRIShapeFileParser}
 */
public class ESRIShapeFileParserTests {

    @Test
    public void testReadFiles() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(
                new File(getClass().getResource("/org/elasticsearch/test/unit/shape/parsers/esri/test.shp").getFile()));
        ByteBuffer shpBuffer = ByteBuffer.allocate(2048000);
        fileInputStream.getChannel().read(shpBuffer);
        shpBuffer.flip();

        InputStream dbfInputStream = new FileInputStream(
                new File(getClass().getResource("/org/elasticsearch/test/unit/shape/parsers/esri/test.dbf").getFile()));

        List<Shape> shapes = ESRIShapeFileParser.parseShpFile(shpBuffer);
        List<Map<String, Object>> shapeMetadata = ESRIShapeFileParser.parseDBFFile(dbfInputStream);

        assertEquals(shapes.size(), 177);
        assertEquals(shapeMetadata.size(), 177);

        dbfInputStream.close();
        fileInputStream.close();
    }
}
