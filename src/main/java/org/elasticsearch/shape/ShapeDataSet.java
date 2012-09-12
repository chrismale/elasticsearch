package org.elasticsearch.shape;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ShapeDataSet {

    String id();

    String nameField();

    List<Map<String, Object>> shapeData() throws IOException;

    void addMetadata(XContentBuilder contentBuilder) throws IOException;
}
