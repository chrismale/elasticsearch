package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link ShapeService} which pre-loads Shapes from the local
 * filesystem and stores newly registered Shapes in memory.
 *
 * Shapes are loaded from files in the config/shapes directory.  Lines of the files
 * are assumed to be "shape_name wkt_shape_definition".
 */
public class LocalShapeService extends AbstractComponent implements ShapeService {

    private final Map<String, Shape> shapesByName = new HashMap<String, Shape>();
    private final ShapeFileReader shapeFileReader;

    @Inject
    public LocalShapeService(Settings settings, Environment environment) {
        super(settings);
        // TODO: make configurable
        this.shapeFileReader = new CustomWKTShapeFileReader(settings);

        File shapeFiles = new File(environment.configFile(), "shapes");
        if (shapeFiles.exists()) {
            readShapes(shapeFiles);
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

            try {
                shapesByName.putAll(shapeFileReader.readFile(file));
            } catch (IOException ioe) {
                logger.error("IOException thrown while reading file [{}]", ioe, file.getName());
                break;
            }
        }
    }
}
