package org.elasticsearch.shape;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.*;

import java.io.IOException;

import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

public class RestShapeDataSetListAction extends BaseRestHandler {

    private final ShapeService shapeService;

    @Inject
    public RestShapeDataSetListAction(Settings settings, Client client, RestController restController, ShapeService shapeService) {
        super(settings, client);
        this.shapeService = shapeService;
        // TODO Terrible name, decide on something better.  _shape is used as the index to store the shapes in
        restController.registerHandler(RestRequest.Method.GET, "/_shapedataset/list", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel) {
        try {
            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                        .startArray("data_set_ids");
            for (ShapeDataSet dataSet : shapeService.dataSets()) {
                builder.value(dataSet.id());
            }
            builder.endArray().endObject();
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
}
