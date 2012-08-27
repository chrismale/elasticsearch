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
 */
public class LocalShapeService extends AbstractComponent implements ShapeService {

    private final Map<String, Shape> shapesByName = new HashMap<String, Shape>();

    @Inject
    public LocalShapeService(Settings settings, Environment environment, ShapeFileReader reader) {
        super(settings);

        File shapeFiles = new File(environment.configFile(), "shapes");
        if (shapeFiles.exists()) {
            try {
                shapesByName.putAll(reader.readFiles(shapeFiles));
            } catch (IOException ioe) {
                logger.error("IOException thrown while reading shape files", ioe);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Shape shape(String name) {
        return shapesByName.get(name);
    }
}
