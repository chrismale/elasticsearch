package org.elasticsearch.index.query;

import com.spatial4j.core.shape.Shape;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.Strings;
import org.elasticsearch.shape.parsers.GeoJSONShapeParser;
import org.elasticsearch.common.lucene.spatial.ShapeRelation;
import org.elasticsearch.shape.ShapeService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.geo.GeoShapeFieldMapper;

import java.io.IOException;

public class GeoShapeQueryParser implements QueryParser {

    public static final String NAME = "geo_shape";

    private final ShapeService shapeService;

    @Inject
    public GeoShapeQueryParser(ShapeService shapeService) {
        this.shapeService = shapeService;
    }

    @Override
    public String[] names() {
        return new String[]{NAME, Strings.toCamelCase(NAME)};
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        String fieldName = null;
        ShapeRelation shapeRelation = null;
        Shape shape = null;

        XContentParser.Token token;
        String currentFieldName = null;
        float boost = 1f;

        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                fieldName = currentFieldName;

                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentFieldName = parser.currentName();

                        token = parser.nextToken();
                        if ("shape".equals(currentFieldName)) {
                            if (token == XContentParser.Token.START_OBJECT) {
                                shape = GeoJSONShapeParser.parse(parser);
                            } else {
                                throw new QueryParsingException(parseContext.index(), "Unsupported shape definition");
                            }
                        } else if ("relation".equals(currentFieldName)) {
                            shapeRelation = ShapeRelation.getRelationByName(parser.text());
                            if (shapeRelation == null) {
                                throw new QueryParsingException(parseContext.index(), "Unknown shape operation [" + parser.text() + " ]");
                            }
                        } else if ("named_shape".equals(currentFieldName)) {
                            String name = null;
                            String type = null;
                            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                                if (token == XContentParser.Token.FIELD_NAME) {
                                    currentFieldName = parser.currentName();
                                } else if (token.isValue()) {
                                    if ("name".equals(currentFieldName)) {
                                        name = parser.text();
                                    } else if ("type".equals(currentFieldName)) {
                                        type = parser.text();
                                    }
                                }
                            }
                            if (name == null || type == null) {
                                throw new QueryParsingException(parseContext.index(), "Named Shape name or type missing");
                            }
                            shape = shapeService.shape(name, type);
                            if (shape == null) {
                                throw new QueryParsingException(parseContext.index(),
                                        "Shape with name [" + name + "] in type [" + type + "] not found");
                            }
                        }
                    }
                }
            } else if (token.isValue()) {
                if ("boost".equals(currentFieldName)) {
                    boost = parser.floatValue();
                }
            }
        }

        if (shape == null) {
            throw new QueryParsingException(parseContext.index(), "No Shape defined");
        }

        if (shapeRelation == null) {
            throw new QueryParsingException(parseContext.index(), "No Shape Relation defined");
        }

        MapperService.SmartNameFieldMappers smartNameFieldMappers = parseContext.smartFieldMappers(fieldName);
        if (smartNameFieldMappers == null || !smartNameFieldMappers.hasMapper()) {
            throw new QueryParsingException(parseContext.index(), "Failed to find geo_shape field [" + fieldName + "]");
        }

        FieldMapper fieldMapper = smartNameFieldMappers.mapper();
        // TODO: This isn't the nicest way to check this
        if (!(fieldMapper instanceof GeoShapeFieldMapper)) {
            throw new QueryParsingException(parseContext.index(), "Field [" + fieldName + "] is not a geo_shape");
        }

        GeoShapeFieldMapper shapeFieldMapper = (GeoShapeFieldMapper) fieldMapper;

        Query query = shapeFieldMapper.spatialStrategy().createQuery(shape, shapeRelation);
        query.setBoost(boost);
        return query;
    }
}
