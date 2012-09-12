package org.elasticsearch.common.lucene.spatial.prefix;

import com.spatial4j.core.shape.Shape;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.operation.buffer.BufferOp;
import com.vividsolutions.jts.operation.buffer.BufferParameters;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.elasticsearch.common.lucene.search.TermFilter;
import org.elasticsearch.common.lucene.search.XBooleanFilter;
import org.elasticsearch.common.lucene.spatial.SpatialStrategy;
import org.elasticsearch.common.lucene.spatial.prefix.tree.Node;
import org.elasticsearch.common.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.elasticsearch.shape.GeoShapeConstants;
import org.elasticsearch.shape.JtsGeometry;
import org.elasticsearch.shape.ShapeBuilder;
import org.elasticsearch.index.mapper.FieldMapper;

import java.util.List;

/**
 * Implementation of {@link SpatialStrategy} that uses TermQuerys / TermFilters
 * to query and filter for Shapes related to other Shapes.
 */
public class TermQueryPrefixTreeStrategy extends SpatialStrategy {

    private static final double CONTAINS_BUFFER_DISTANCE = 0.5;
    private static final BufferParameters BUFFER_PARAMETERS = new BufferParameters(3, BufferParameters.CAP_SQUARE);

    /**
     * Creates a new TermQueryPrefixTreeStrategy
     *
     * @param fieldName Name of the field the Strategy applies to
     * @param prefixTree SpatialPrefixTree that will be used to represent Shapes
     * @param distanceErrorPct Distance Error Percentage used to guide the
     *                         SpatialPrefixTree on how precise it should be
     */
    public TermQueryPrefixTreeStrategy(FieldMapper.Names fieldName, SpatialPrefixTree prefixTree, double distanceErrorPct) {
        super(fieldName, prefixTree, distanceErrorPct);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter createIntersectsFilter(Shape shape) {
        int detailLevel = getPrefixTree().getMaxLevelForPrecision(shape, getDistanceErrorPct());
        List<Node> nodes = getPrefixTree().getNodes(shape, detailLevel, false);

        Term[] nodeTerms = new Term[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            nodeTerms[i] = getFieldName().createIndexNameTerm(nodes.get(i).getTokenString());
        }
        return new XTermsFilter(nodeTerms);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query createIntersectsQuery(Shape shape) {
        int detailLevel = getPrefixTree().getMaxLevelForPrecision(shape, getDistanceErrorPct());
        List<Node> nodes = getPrefixTree().getNodes(shape, detailLevel, false);

        BooleanQuery query = new BooleanQuery();
        for (Node node : nodes) {
            query.add(new TermQuery(getFieldName().createIndexNameTerm(node.getTokenString())),
                    BooleanClause.Occur.SHOULD);
        }

        return new ConstantScoreQuery(query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter createDisjointFilter(Shape shape) {
        int detailLevel = getPrefixTree().getMaxLevelForPrecision(shape, getDistanceErrorPct());
        List<Node> nodes = getPrefixTree().getNodes(shape, detailLevel, false);

        XBooleanFilter filter = new XBooleanFilter();
        for (Node node : nodes) {
            filter.addNot(new TermFilter(getFieldName().createIndexNameTerm(node.getTokenString())));
        }

        return filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query createDisjointQuery(Shape shape) {
        int detailLevel = getPrefixTree().getMaxLevelForPrecision(shape, getDistanceErrorPct());
        List<Node> nodes = getPrefixTree().getNodes(shape, detailLevel, false);

        BooleanQuery query = new BooleanQuery();
        query.add(new MatchAllDocsQuery(), BooleanClause.Occur.SHOULD);
        for (Node node : nodes) {
            query.add(new TermQuery(getFieldName().createIndexNameTerm(node.getTokenString())),
                    BooleanClause.Occur.MUST_NOT);
        }

        return new ConstantScoreQuery(query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter createContainsFilter(Shape shape) {
        Filter intersectsFilter = createIntersectsFilter(shape);

        Geometry shapeGeometry = ShapeBuilder.toJTSGeometry(shape);
        // TODO: Need some way to detect if having the buffer is going to push the shape over the dateline
        // and throw an error in this instance
        Geometry buffer = BufferOp.bufferOp(shapeGeometry, CONTAINS_BUFFER_DISTANCE, BUFFER_PARAMETERS);
        Shape bufferedShape = new JtsGeometry(buffer.difference(shapeGeometry), GeoShapeConstants.SPATIAL_CONTEXT);
        Filter bufferedFilter = createIntersectsFilter(bufferedShape);

        XBooleanFilter filter = new XBooleanFilter();
        filter.addShould(intersectsFilter);
        filter.addNot(bufferedFilter);

        return filter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query createContainsQuery(Shape shape) {
        Query intersectsQuery = createIntersectsQuery(shape);

        Geometry shapeGeometry = ShapeBuilder.toJTSGeometry(shape);
        Geometry buffer = BufferOp.bufferOp(shapeGeometry, CONTAINS_BUFFER_DISTANCE, BUFFER_PARAMETERS);
        Shape bufferedShape = new JtsGeometry(buffer.difference(shapeGeometry), GeoShapeConstants.SPATIAL_CONTEXT);
        Query bufferedQuery = createIntersectsQuery(bufferedShape);

        BooleanQuery query = new BooleanQuery();
        query.add(intersectsQuery, BooleanClause.Occur.SHOULD);
        query.add(bufferedQuery, BooleanClause.Occur.MUST_NOT);

        return new ConstantScoreQuery(query);
    }
}
