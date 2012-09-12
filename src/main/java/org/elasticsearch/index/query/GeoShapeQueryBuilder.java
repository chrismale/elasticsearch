package org.elasticsearch.index.query;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.shape.parsers.GeoJSONShapeSerializer;
import org.elasticsearch.common.lucene.spatial.ShapeRelation;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

/**
 * {@link QueryBuilder} that builds a GeoShape query
 */
public class GeoShapeQueryBuilder extends BaseQueryBuilder implements BoostableQueryBuilder<GeoShapeQueryBuilder> {

    private final String name;
    private final Shape shape;
    private final String shapeName;
    private final String type;

    private ShapeRelation relation = ShapeRelation.INTERSECTS;

    private float boost = -1;

    /**
     * Creates a new GeoShapeQueryBuilder whose Query will be against the
     * given field name and will use the given Shape to query
     *
     * @param name Name of the field that will be queried
     * @param shape Shape used in the filter
     */
    public GeoShapeQueryBuilder(String name, Shape shape) {
        this.name = name;
        this.shape = shape;
        this.shapeName = null;
        this.type = null;
    }

    /**
     * Creates a new GeoShapeQueryBuilder whose Query will be against the
     * given field name and use a stored Shape with the given name in the query
     *
     * @param name Name of the field that will be queried
     * @param shapeName Name of a stored Shape that will be used in the query
     */
    public GeoShapeQueryBuilder(String name, String shapeName, String type) {
        this.name = name;
        this.shape = null;
        this.shapeName = shapeName;
        this.type = type;
    }

    /**
     * Sets the {@link ShapeRelation} that defines how the Shape used in the
     * Query must relate to indexed Shapes
     *
     * @param relation ShapeRelation used in the query
     * @return this
     */
    public GeoShapeQueryBuilder relation(ShapeRelation relation) {
        this.relation = relation;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeoShapeQueryBuilder boost(float boost) {
        this.boost = boost;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(GeoShapeQueryParser.NAME);

        builder.startObject(name);
        builder.field("relation", relation.getRelationName());

        if (shape != null) {
            builder.startObject("shape");
            GeoJSONShapeSerializer.serialize(shape, builder);
            builder.endObject();
        } else {
            builder.startObject("named_shape")
                    .field("name", shapeName)
                    .field("type", type)
                    .endObject();
        }

        builder.endObject();

        if (boost != -1) {
            builder.field("boost", boost);
        }

        builder.endObject();
    }

}
