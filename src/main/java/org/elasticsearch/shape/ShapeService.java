package org.elasticsearch.shape;

import com.spatial4j.core.shape.Shape;
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
    public static final String INDEX_NAME = "_shape";

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

    public void index(ShapeDataSet dataSet, String type) throws IOException {
        // TODO: What does CreateIndex do if the index already exists?
        client.admin().indices().create(new CreateIndexRequest(INDEX_NAME));

        List<Map<String, Object>> shapeData = dataSet.shapeData();

        Date insertDate = new Date();

        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();

        for (Map<String, Object> data : shapeData) {
            // TODO: Consider excluding DUMMY_SHAPE shapes
            Shape shape = (Shape) data.remove("shape");

            XContentBuilder contentBuilder = XContentFactory.jsonBuilder().startObject();

            // TODO Consider embedding properties in a named object
            // Cannot use XContentBuilder.map since it starts and ends an object
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                contentBuilder.field(entry.toString(), entry.getValue());
            }

            contentBuilder.startObject("shape");
            GeoJSONShapeSerializer.serialize(shape, contentBuilder);
            contentBuilder.endObject();

            contentBuilder.startObject("metadata")
                    .field("dataset_id", dataSet.id())
                    .field("insert_date", insertDate);
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
            // TODO: What to do?
            throw new ElasticSearchIllegalStateException();
        }
    }

    public Shape shape(String name, String type) throws IOException {
        GetResponse response = client.prepareGet(INDEX_NAME, type, name).execute().actionGet();
        XContentParser parser = JsonXContent.jsonXContent.createParser(response.source());
        XContentParser.Token currentToken;
        while ((currentToken = parser.currentToken()) != XContentParser.Token.END_OBJECT) {
            if (currentToken == XContentParser.Token.FIELD_NAME) {
                if ("shape".equals(parser.currentName())) {
                    parser.nextToken();
                    return GeoJSONShapeParser.parse(parser);
                }
            }
        }
        return null;
    }
}
