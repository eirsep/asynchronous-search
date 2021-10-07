/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.search.asynchronous.rest;

import org.opensearch.client.node.NodeClient;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestStatusToXContentListener;
import org.opensearch.search.asynchronous.response.DeletePITRequest;
import org.opensearch.search.asynchronous.transport.DeletePITAction;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static org.opensearch.rest.RestRequest.Method.DELETE;
import static org.opensearch.rest.RestRequest.Method.POST;

public class RestDeletePITAction extends BaseRestHandler {
    @Override
    public String getName() {
        return "delete_pit_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        DeletePITRequest deletePitRequest = new DeletePITRequest(request.param("pit_id"));
        return channel -> client.execute(DeletePITAction.INSTANCE, deletePitRequest, new RestStatusToXContentListener<>(channel));
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(Collections.singletonList(
                new Route(DELETE, "/_pit/{pit_id}")));
    }
}
