package org.elasticsearch.shape;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.*;

import java.io.IOException;

import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

public class RestShapeDataSetIndexAction extends BaseRestHandler {

    private final ShapeService shapeService;

    @Inject
    public RestShapeDataSetIndexAction(Settings settings, Client client, RestController restController, ShapeService shapeService) {
        super(settings, client);
        this.shapeService = shapeService;
        // TODO Terrible name, decide on something better.  _shape is used as the index to store the shapes in
        restController.registerHandler(RestRequest.Method.GET, "/_shapedataset/index", this);
    }

    public void handleRequest(final RestRequest request, final RestChannel channel) {
        try {
            String dataSetId = request.param(Fields.DATASET_ID);
            ShapeDataSet dataSet = shapeService.dataSet(dataSetId);

            if (dataSet == null) {
                XContentBuilder builder = restContentBuilder(request)
                        .startObject()
                            .field(Fields.RESULT, "ShapeDataSet with ID [" + dataSetId + "] not found")
                        .endObject();
                channel.sendResponse(new XContentRestResponse(request, RestStatus.NOT_FOUND, builder));
            }

            String type = request.param(Fields.TYPE);
            if (type == null) {
                XContentBuilder builder = restContentBuilder(request)
                        .startObject()
                            .field(Fields.RESULT, "type missing")
                        .endObject();
                channel.sendResponse(new XContentRestResponse(request, RestStatus.BAD_REQUEST, builder));
            }

            int shapeCount = shapeService.index(dataSet, type);
            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                        .field(Fields.RESULT, shapeCount + " shapes indexed")
                    .endObject();
            channel.sendResponse(new XContentRestResponse(request, RestStatus.OK, builder));
        } catch (IOException ioe) {
            onFailure(ioe, request, channel);
        }
    }

    private void onFailure(Exception e, RestRequest request, RestChannel channel) {
        try {
            channel.sendResponse(new XContentThrowableRestResponse(request, e));
        } catch (IOException ioe) {
            logger.error("Failed to send error", ioe);
        }
    }

    private static interface Fields {
        String RESULT = "result";
        String DATASET_ID = "data_set_id";
        String TYPE = "type";
    }
}
