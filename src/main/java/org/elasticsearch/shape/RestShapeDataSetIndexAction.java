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
        restController.registerHandler(RestRequest.Method.GET, "/_shapedataset/index/", this);
    }

    public void handleRequest(final RestRequest request, final RestChannel channel) {
        String dataSetId = request.param(Fields.DATASET_ID);
        ShapeDataSet dataSet = shapeService.dataSet(dataSetId);

        if (dataSet == null) {
            try {
                XContentBuilder builder = restContentBuilder(request)
                        .startObject()
                            .field(Fields.RESULT, "ShapeDataSet with ID [" + dataSetId + "] not found")
                        .endObject();
                channel.sendResponse(new XContentRestResponse(request, RestStatus.NOT_FOUND, builder));
            } catch (IOException ioe) {
                try {
                    channel.sendResponse(new XContentThrowableRestResponse(request, ioe));
                } catch (IOException e) {
                    logger.error("Failed to send error", e);
                }
            }
        }

        String type = request.param(Fields.TYPE);
        // TODO Validate that type value is present.  Is there some way to automate this?

        try {
            shapeService.index(dataSet, type);
            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                        .field(Fields.RESULT, "ShapeDataSet [" + dataSetId + "] indexed")
                    .endObject();
            channel.sendResponse(new XContentRestResponse(request, RestStatus.OK, builder));
        } catch (IOException ioe) {
            try {
                channel.sendResponse(new XContentThrowableRestResponse(request, ioe));
            } catch (IOException e) {
                logger.error("Failed to send error", e);
            }
        }
    }

    private static interface Fields {
        String DATASET_ID = "dataSetId";
        String TYPE = "type";
        String RESULT = "result";
    }
}
