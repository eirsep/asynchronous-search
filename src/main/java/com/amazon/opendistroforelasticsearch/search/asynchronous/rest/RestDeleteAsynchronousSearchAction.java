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
/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.search.asynchronous.rest;

import com.amazon.opendistroforelasticsearch.search.asynchronous.action.DeleteAsynchronousSearchAction;
import com.amazon.opendistroforelasticsearch.search.asynchronous.plugin.AsynchronousSearchPlugin;
import com.amazon.opendistroforelasticsearch.search.asynchronous.request.DeleteAsynchronousSearchRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestStatusToXContentListener;

import java.util.Collections;
import java.util.List;

import static com.amazon.opendistroforelasticsearch.search.asynchronous.plugin.AsynchronousSearchPlugin.BASE_URI;
import static org.opensearch.rest.RestRequest.Method.DELETE;

public class RestDeleteAsynchronousSearchAction extends BaseRestHandler {

    @Override
    public String getName() {
        return "delete_asynchronous_search";
    }

    @Override
    public List<Route> routes() {
        return Collections.emptyList();
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return Collections.singletonList(new ReplacedRoute(DELETE, BASE_URI + "/{id}",
                DELETE, AsynchronousSearchPlugin.LEGACY_OPENDISTRO_BASE_URI + "/{id}")
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        DeleteAsynchronousSearchRequest deleteRequest = new DeleteAsynchronousSearchRequest(request.param("id"));
        return channel -> {
            client.execute(DeleteAsynchronousSearchAction.INSTANCE, deleteRequest, new RestStatusToXContentListener<>(channel));
        };
    }
}
