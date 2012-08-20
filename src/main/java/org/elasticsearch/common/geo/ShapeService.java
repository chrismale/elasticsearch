package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;

/**
 * Service where Shapes can be pre-loaded, stored at runtime and retrieved
 */
public interface ShapeService {

    /**
     * Adds the given Shape with the given name to the Service.
     *
     * @param name Name of the Shape
     * @param shape Shape to store
     */
    void add(String name, Shape shape);

    /**
     * Retrieves the Shape with the given name
     *
     * @param name Name of the Shape to retrieve
     * @return Shape with the given name or {@code null} if no Shape with the name exists
     */
    Shape shape(String name);
}
