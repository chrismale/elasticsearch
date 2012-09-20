package org.elasticsearch.shape;

import com.spatial4j.core.shape.Shape;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.ElasticSearchIllegalStateException;
import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.shape.parsers.GeoJSONShapeParser;
import org.elasticsearch.shape.parsers.GeoJSONShapeSerializer;

import java.io.IOException;
import java.util.*;

/**
 * Service where Shapes can be pre-loaded and retrieved
 */
public class ShapeService extends AbstractComponent {

    // TODO: Make this configurable?
    public static final String INDEX_NAME = "shape";

    private final List<ShapeDataSet> dataSets = new ArrayList<ShapeDataSet>();
    private final Map<String, ShapeDataSet> dataSetsById = new HashMap<String, ShapeDataSet>();

    private final Client client;

    @Inject
    public ShapeService(Client client, Settings settings) {
        super(settings);
        this.client = client;

        register(GeoShapeConstants.NATURAL_EARTH_DATA_COUNTRIES);
    }

    public void register(ShapeDataSet dataSet) {
        dataSets.add(dataSet);
        dataSetsById.put(dataSet.id(), dataSet);
    }

    public List<ShapeDataSet> dataSets() {
        return dataSets;
    }

    public ShapeDataSet dataSet(String id) {
        return dataSetsById.get(id);
    }

    public int index(ShapeDataSet dataSet, String type) throws IOException {
        List<Map<String, Object>> shapeData = dataSet.shapeData();

        Date insertDate = new Date();

        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();

        for (Map<String, Object> data : shapeData) {
            // TODO: Consider excluding DUMMY_SHAPE shapes
            Shape shape = (Shape) data.remove(Fields.SHAPE);

            XContentBuilder contentBuilder = XContentFactory.jsonBuilder().startObject();

            // TODO Consider embedding properties in a named object
            // Cannot use XContentBuilder.map since it starts and ends an object
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                contentBuilder.field(entry.getKey(), entry.getValue());
            }

            contentBuilder.startObject(Fields.SHAPE);
            GeoJSONShapeSerializer.serialize(shape, contentBuilder);
            contentBuilder.endObject();

            contentBuilder.startObject(Fields.METADATA)
                    .field(Fields.DATASET_ID, dataSet.id())
                    .field(Fields.INSERT_DATE, insertDate);
            dataSet.addMetadata(contentBuilder);
            contentBuilder.endObject();

            contentBuilder.endObject();

            String name = (String) data.get(dataSet.nameField());
            if (name == null) {
                throw new ElasticSearchParseException("Name field [" + dataSet.nameField() + "] not found in metadata");
            }

            bulkRequestBuilder.add(client.prepareIndex(INDEX_NAME, type, name).setSource(contentBuilder).request());
        }

        BulkResponse response = bulkRequestBuilder.execute().actionGet();
        if (response.hasFailures()) {
            throw new ElasticSearchIllegalStateException(response.buildFailureMessage());
        }

        return shapeData.size();
    }

    public Shape shape(String name, String type) throws IOException {
        GetResponse response = client.prepareGet(INDEX_NAME, type, name).setPreference("_local").execute().actionGet();
        if (!response.exists()) {
            throw new ElasticSearchIllegalArgumentException("Shape with name [" + name + "] in type [" + type + "] not found");
        }

        XContentParser parser = JsonXContent.jsonXContent.createParser(response.source());
        XContentParser.Token currentToken;
        while ((currentToken = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (currentToken == XContentParser.Token.FIELD_NAME) {
                if (Fields.SHAPE.equals(parser.currentName())) {
                    parser.nextToken();
                    return GeoJSONShapeParser.parse(parser);
                }
            }
        }
        throw new ElasticSearchIllegalStateException("Shape with name [" + name + "] found but missing " + Fields.SHAPE + " field");
    }

    public static interface Fields {
        public final String SHAPE = "shape";
        public final String METADATA = "metadata";
        public final String DATASET_ID = "data_set_id";
        public final String INSERT_DATE = "insert_date";
    }
}
