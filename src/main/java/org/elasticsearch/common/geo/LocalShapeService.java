package org.elasticsearch.common.geo;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.env.Environment;

import java.io.*;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of {@link ShapeService} which pre-loads Shapes from the local
 * filesystem and stores newly registered Shapes in memory.
 *
 * Shapes are loaded from files in the config/shapes directory.  Lines of the files
 * are assumed to be "shape_name wkt_shape_definition".
 */
public class LocalShapeService extends AbstractComponent implements ShapeService {

    private final ConcurrentMap<String, Shape> shapesByName = ConcurrentCollections.newConcurrentMap();

    @Inject
    public LocalShapeService(Settings settings, Environment environment) {
        super(settings);

        File shapeFiles = new File(environment.configFile(), "shapes");
        if (shapeFiles.exists()) {
            readShapes(shapeFiles);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void add(String name, Shape shape, boolean override) {
        if (override) {
            shapesByName.put(name, shape);
        } else {
            shapesByName.putIfAbsent(name, shape);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Shape shape(String name) {
        return shapesByName.get(name);
    }

    /**
     * Reads any in the Shapes defined in the given file, or its children
     * files if it is a directory.
     *
     * @param shapesFiles File to read Shapes from, or a directory containing
     *                    Shape files.
     */
    private void readShapes(File shapesFiles) {
        for (File file : shapesFiles.listFiles()) {
            if (file.isDirectory()) {
                readShapes(file);
            }

            final WKTShapeParser shapeParser = new WKTShapeParser();

            LineProcessor<Void> lineProcessor = new LineProcessor<Void>() {

                @Override
                public boolean processLine(String line) throws IOException {
                    try {
                        int space = line.indexOf(' ');
                        String name = line.substring(0, space);
                        Shape shape = shapeParser.parse(line.substring(space + 1));
                        shapesByName.put(name, shape);
                        return true;
                    } catch (ParseException pe) {
                        logger.error("ParseException thrown while parsing Shape definition", pe);
                        return false;
                    }
                }

                @Override
                public Void getResult() {
                    return null;
                }
            };

            try {
                Files.readLines(file, Charset.forName("UTF-8"), lineProcessor);
            } catch (IOException ioe) {
                logger.error("IOException thrown while reading file [{}]", ioe, file.getName());
                break;
            }
        }
    }
}
