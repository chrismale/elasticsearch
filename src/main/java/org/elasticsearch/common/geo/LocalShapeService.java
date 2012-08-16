package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.env.Environment;

import java.io.File;
import java.util.concurrent.ConcurrentMap;

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

    public void add(String name, Shape shape, boolean override) {
        if (override) {
            shapesByName.put(name, shape);
        } else {
            shapesByName.putIfAbsent(name, shape);
        }
    }

    public Shape shape(String name) {
        return shapesByName.get(name);
    }

    private void readShapes(File shapesFiles) {
        for (File file : shapesFiles.listFiles()) {
            if (file.isDirectory()) {
                readShapes(file);
            }

        }
    }
}
