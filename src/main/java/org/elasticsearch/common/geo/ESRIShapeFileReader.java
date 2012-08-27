package org.elasticsearch.common.geo;

import com.google.common.io.Closeables;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.spatial4j.core.shape.Shape;
import com.spatial4j.core.shape.jts.JtsGeometry;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LinearRing;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Simple implementation of a parser of ESRI ShapeFiles.  Implementation is derived from
 * the technical description of the file formats provided in http://www.esri.com/library/whitepapers/pdfs/shapefile.pdf.
 * <p>
 * Supported Shape types are:
 * <ul>
 * <li>Polygon</li>
 * <li>Point</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("unused")
public class ESRIShapeFileReader implements ShapeFileReader {

    private static final int FILE_CODE = 9994;
    private static final int VERSION = 1000;

    /**
     * Enum of the ShapeTypes currently supported in this impl, along with their
     * codes as found in shp files.
     */
    private static enum ShapeType {

        POINT(1),
        POLYGON(5);

        private final int value;

        ShapeType(int value) {
            this.value = value;
        }

        /**
         * Gets the {@link ShapeType} which is represented with the given value in files
         *
         * @param value Value of the ShapeType to find
         * @return ShapeType with the given value
         * @throws ElasticSearchIllegalArgumentException Thrown if no ShapeType with the
         *         value exists
         */
        public static ShapeType getShapeTypeForValue(int value) {
            for (ShapeType shapeType : ShapeType.values()) {
                if (shapeType.value == value) {
                    return shapeType;
                }
            }
            throw new ElasticSearchIllegalArgumentException("Unknown ShapeType with value [" + value + "]");
        }
    }

    private final String nameField;

    /**
     * Creates a new ESRIShapeFileReader.
     *
     * @param settings Configuration settings.  Currently "shape.name.field" can be used
     *                 to specify the name of the field in the DBF file that has the
     *                 Shape names.
     */
    @Inject
    public ESRIShapeFileReader(Settings settings) {
        this.nameField = settings.get("shapefile.name.field", "name");
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Shape> readFiles(File shapeDirectory) throws IOException {
        Map<String, Shape> shapesByName = new HashMap<String, Shape>();

        Set<File> shpFiles = new HashSet<File>();

        for (File file : shapeDirectory.listFiles()) {
            if (file.isDirectory()) {
                shapesByName.putAll(readFiles(file));
            } else if (file.getName().endsWith(".shp")) {
                shpFiles.add(file);
            }
        }

        for (File shpFile : shpFiles) {
            String shpPath = shpFile.getPath();
            String dbfFileName = shpPath.substring(0, shpPath.length() - 3) + "dbf";
            File dbfFile = new File(dbfFileName);

            if (dbfFile.exists()) {
                shapesByName.putAll(parseFiles(shpFile, dbfFile));
            }
        }

        return shapesByName;
    }

    /**
     * Parses the given SHP file and DBF file, extracting the Shapes and their names
     *
     * @param shpFile SHP file
     * @param dbfFile DBF file
     * @return Shapes with their names
     * @throws IOException Can be thrown if there is a problem reading the files
     */
    public Map<String, Shape> parseFiles(File shpFile, File dbfFile) throws IOException {
        List<Shape> shapes = parseShpFile(shpFile);
        return parseDBFFile(dbfFile, shapes);
    }

    /**
     * Parses the SHP file, extracting the Shapes contained
     *
     * @param file SHP file to parse
     * @return List of Shapes contained in the file
     * @throws IOException Can be thrown if there is a problem reading the SHP File
     */
    private List<Shape> parseShpFile(File file) throws IOException {
        FileInputStream inputStream = null;
        FileChannel fileChannel = null;

        try {
            inputStream = new FileInputStream(file);
            fileChannel = inputStream.getChannel();

            ShapeType shapeType = parseHeader(fileChannel);

            List<Shape> shapes = new ArrayList<Shape>();

            while (fileChannel.position() < fileChannel.size()) {
                shapes.add(parseRecord(fileChannel, shapeType));
            }

            return shapes;
        } finally {
            Closeables.closeQuietly(inputStream);
            Closeables.closeQuietly(fileChannel);
        }
    }

    /**
     * Parses the SHP file header.  Note, only the type of Shapes contained in
     * the file is returned.  All other information is read, validated, and discarded.
     *
     * @param fileChannel FileChannel to read the file contents from
     * @return {@link ShapeType} representing the types of Shapes contained in the file
     * @throws IOException Can be thrown if there is a problem reading from the file
     */
    private ShapeType parseHeader(FileChannel fileChannel) throws IOException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(100);
        fileChannel.read(headerBuffer);
        headerBuffer.flip();

        int fileCode = headerBuffer.getInt();
        if (fileCode != FILE_CODE) {
            throw new ElasticSearchParseException("Header does not have correct file code. " +
                    "Expected [" + FILE_CODE + "] but found [" + fileCode + "]");
        }

        // Unused blocks of data
        headerBuffer.getInt();
        headerBuffer.getInt();
        headerBuffer.getInt();
        headerBuffer.getInt();
        headerBuffer.getInt();

        // We don't need to keep track of this since our FileChannel knows the full
        // file size
        int fileLength = headerBuffer.getInt();

        headerBuffer.order(ByteOrder.LITTLE_ENDIAN);

        int version = headerBuffer.getInt();
        if (version != VERSION) {
            throw new ElasticSearchParseException("Header does not have correct version. " +
                    "Expected [" + VERSION + "] but found [" + version + "]");
        }

        ShapeType shapeType = ShapeType.getShapeTypeForValue(headerBuffer.getInt());

        // Maximum Bounding Rectangle (MBR) for all Shapes
        double minX = headerBuffer.getDouble();
        double minY = headerBuffer.getDouble();
        double maxX = headerBuffer.getDouble();
        double maxY = headerBuffer.getDouble();
        double minZ = headerBuffer.getDouble();
        double maxZ = headerBuffer.getDouble();
        double minM = headerBuffer.getDouble();
        double maxM = headerBuffer.getDouble();

        return shapeType;
    }

    /**
     * Parses the Shape record at the current position in the SHP file
     *
     * @param fileChannel FileChannel for reading from the SHP file
     * @param shapeType Type of Shape that will be read
     * @return Shape read from the SHP File
     * @throws IOException Can be thrown if there is a problem reading from the file
     */
    private Shape parseRecord(FileChannel fileChannel, ShapeType shapeType) throws IOException {
        ByteBuffer headerBuffer = ByteBuffer.allocate(8);
        fileChannel.read(headerBuffer);
        headerBuffer.flip();

        // Record number is ignored, we assume the records are in order
        int recordNumber = headerBuffer.getInt();

        int contentLength = headerBuffer.getInt();
        // Length is defined as 16-bit words in file
        ByteBuffer contentBuffer = ByteBuffer.allocate(contentLength * 2);
        fileChannel.read(contentBuffer);
        contentBuffer.flip();

        if (shapeType == ShapeType.POLYGON) {
            return parsePolygon(contentBuffer);
        } else {
            throw new UnsupportedOperationException("ShapeType [" + shapeType.name() + "] not currently supported");
        }
    }

    /**
     * Parses a Polygon shape from the contents of the given ByteBuffer
     *
     * @param polygonBuffer ByteBuffer holding the representation of a polygon
     * @return Parsed Polygon
     */
    private Shape parsePolygon(ByteBuffer polygonBuffer) {
        polygonBuffer.order(ByteOrder.LITTLE_ENDIAN);

        int shapeType = polygonBuffer.getInt();
        if (shapeType != ShapeType.POLYGON.value) {
            throw new ElasticSearchParseException("Polygon record does not have correct ShapeType. " +
                    "Expected [" + ShapeType.POLYGON.value + "] but found [" + shapeType + "]");
        }

        double minX = polygonBuffer.getDouble();
        double minY = polygonBuffer.getDouble();
        double maxX = polygonBuffer.getDouble();
        double maxY = polygonBuffer.getDouble();

        int numParts = polygonBuffer.getInt();
        int numPoints = polygonBuffer.getInt();

        int[] parts = new int[numParts];

        for (int i = 0; i < numParts; i++) {
            parts[i] = polygonBuffer.getInt();
        }

        List<Coordinate> points = new ArrayList<Coordinate>();

        for (int i = 0; i < numPoints; i++) {
            points.add(parseCoordinates(polygonBuffer));
        }

        List<LinearRing> rings = new ArrayList<LinearRing>(numParts);
        int lastPointer = points.size();

        for (int i = parts.length - 1; i >= 0; i--) {
            int pointer = parts[i];
            List<Coordinate> ringPoints = points.subList(pointer, lastPointer);
            rings.add(GeoShapeConstants.GEOMETRY_FACTORY.createLinearRing(ringPoints.toArray(new Coordinate[ringPoints.size()])));
            lastPointer = pointer;
        }

        LinearRing shell = rings.get(rings.size() - 1);
        LinearRing[] holes = null;
        if (rings.size() > 1) {
            holes = new LinearRing[rings.size() - 1];
            for (int i = 0; i < holes.length; i++) {
                holes[i] = rings.get(i);
            }
        }

        return new JtsGeometry(GeoShapeConstants.GEOMETRY_FACTORY.createPolygon(shell, holes));
    }

    /**
     * Parses coordinates X and Y from the given Buffer.  Note, in the technical
     * description these are referred to as Point datatypes however they do not
     * include the ShapeType value in the file therefore they are not proper Points,
     * instead they are just coordinates.
     *
     * @param coordinateBuffer Buffer containing the coordinates to read
     * @return Coordinate holding the X and Y values
     */
    private Coordinate parseCoordinates(ByteBuffer coordinateBuffer) {
        coordinateBuffer.order(ByteOrder.LITTLE_ENDIAN);

        double x = coordinateBuffer.getDouble();
        double y = coordinateBuffer.getDouble();
        return new Coordinate(x, y);
    }

    /**
     * Parses the DBF file, extracting the name property if defined, and associating
     * it with the appropriate Shapes.  Note, it is assumed that the Shapes in the given
     * List are in the same order as in the DBF file.
     *
     * @param file DBF File to parse
     * @param shapes List of Shapes to associate names with
     * @return Shapes with their associated names
     * @throws IOException Can be thrown if there is a problem reading from the file
     */
    private Map<String, Shape> parseDBFFile(File file, List<Shape> shapes) throws IOException {
        InputStream inputStream = null;

        try {
            inputStream = new FileInputStream(file);
            DBFReader reader = new DBFReader(inputStream);

            int numFields = reader.getFieldCount();
            int numRecords = reader.getRecordCount();

            int nameFieldNumber = -1;

            for (int i = 0; i < numFields; i++) {
                DBFField field = reader.getField(i);
                if (nameField.equals(field.getName().toLowerCase())) {
                    nameFieldNumber = i;
                }
            }

            if (nameFieldNumber == -1) {
                throw new ElasticSearchParseException("Name field [" + nameField + "] not found in DBF file");
            }

            Map<String, Shape> shapesByName = new HashMap<String, Shape>();

            int recordNumber = 0;

            Object[] record;
            while ((record = reader.nextRecord()) != null) {
                String name = ((String) record[nameFieldNumber]).trim();
                if (name.length() > 0) {
                    shapesByName.put(name, shapes.get(recordNumber++));
                }
            }
            return shapesByName;
        } finally {
            Closeables.closeQuietly(inputStream);
        }
    }
}
