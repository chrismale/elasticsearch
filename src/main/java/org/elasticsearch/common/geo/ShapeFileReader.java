package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Abstraction of the logic of reading files containing Shapes.  This is used by
 * {@link LocalShapeService} to read the Shapes in pre-defined files into memory.
 */
public interface ShapeFileReader {

    /**
     * Reads any or all of the files in the given Directory, extracting their Shapes
     * and names.  Note, it is assumed that implementations will recursively invoke
     * this method for sub-directories.
     *
     * @param shapeDirectory Directory of files to read
     * @return Shapes with their names
     * @throws IOException Can be thrown if there is a problem reading the files
     */
    Map<String, Shape> readFiles(File shapeDirectory) throws IOException;
}
