package org.elasticsearch.shape;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.spatial4j.core.shape.Shape;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.shape.parsers.ESRIShapeFileParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RemoteESRIShapeDataSet implements ShapeDataSet {

    private final String SHP_SUFFIX = ".shp";
    private final String DBF_SUFFIX = ".dbf";

    private final String id;
    private final URL url;
    private final String nameField;

    public RemoteESRIShapeDataSet(String id, String url, String nameField) {
        this.id = id;
        this.nameField = nameField;
        try {
            // Construct it ourselves so that fields don't have to catch exception
            this.url = new URL(url);
        } catch (MalformedURLException mue) {
            throw new ElasticSearchIllegalArgumentException("Invalid URL for data set", mue);
        }
    }

    public String id() {
        return id;
    }

    public String nameField() {
        return nameField;
    }

    public List<Map<String, Object>> shapeData() throws IOException {
        InputStream urlInputStream = null;
        ZipInputStream zipInputStream = null;

        try {
            urlInputStream = url.openStream();
            zipInputStream = new ZipInputStream(urlInputStream);

            List<Shape> shapes = null;
            List<Map<String, Object>> shapeMetadata = null;

            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String name = zipEntry.getName();

                if (name.endsWith(SHP_SUFFIX)) {
                    shapes = ESRIShapeFileParser.parseShpFile(ByteBuffer.wrap(ByteStreams.toByteArray(zipInputStream)));
                } else if (name.endsWith(DBF_SUFFIX)) {
                    // For some reason javadbf fails when reading directly from the ZIPInputStream
                    // But works when read from a ByteArrayInputStream.
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(ByteStreams.toByteArray(zipInputStream));
                    shapeMetadata = ESRIShapeFileParser.parseDBFFile(byteArrayInputStream);
                }

                zipInputStream.closeEntry();
            }

            if (shapes == null) {
                throw new ElasticSearchIllegalStateException("Data set does not contain SHP file");
            } else if (shapeMetadata == null) {
                throw new ElasticSearchIllegalStateException("Data set does not contain DBF file");
            }

            for (int i = 0; i < shapes.size(); i++) {
                shapeMetadata.get(i).put(ShapeService.Fields.SHAPE, shapes.get(i));
            }

            return shapeMetadata;
        } finally {
            Closeables.closeQuietly(zipInputStream);
            Closeables.closeQuietly(urlInputStream);
        }
    }

    public void addMetadata(XContentBuilder contentBuilder) throws IOException {
        contentBuilder.field("source_url", url.toExternalForm());
    }
}
