package org.elasticsearch.common.geo;

import com.spatial4j.core.shape.Shape;

public interface ShapeService {

    void add(String name, Shape shape, boolean override);

    Shape shape(String name);
}
