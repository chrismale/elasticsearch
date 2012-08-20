package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface ShapeFileReader {

    Map<String, Shape> readFile(File file) throws IOException;
}
