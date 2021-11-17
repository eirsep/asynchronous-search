/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.search.asynchronous.rest;

import org.opensearch.search.asynchronous.action.SubmitAsynchronousSearchAction;
import org.opensearch.search.asynchronous.request.SubmitAsynchronousSearchRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.node.NodeClient;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestCancellableNodeClient;
import org.opensearch.rest.action.RestStatusToXContentListener;
import org.opensearch.rest.action.search.RestSearchAction;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import static org.opensearch.rest.RestRequest.Method.POST;
import static org.opensearch.search.asynchronous.plugin.AsynchronousSearchPlugin.BASE_URI;
import static org.opensearch.search.asynchronous.plugin.AsynchronousSearchPlugin.LEGACY_OPENDISTRO_BASE_URI;

public class RestSubmitAsynchronousSearchAction extends BaseRestHandler {
    /**
     * Indicates whether hits.total should be rendered as an integer or an object
     * in the rest search response.
     */
    public static final String TOTAL_HITS_AS_INT_PARAM = "rest_total_hits_as_int";
    public static final String TYPED_KEYS_PARAM = "typed_keys";
    private static final Set<String> RESPONSE_PARAMS;

    static {
        final Set<String> responseParams = new HashSet<>(Arrays.asList(TYPED_KEYS_PARAM, TOTAL_HITS_AS_INT_PARAM));
        RESPONSE_PARAMS = Collections.unmodifiableSet(responseParams);
    }

    @Override
    public String getName() {
        return "submit_asynchronous_search_action";
    }

    @Override
    public List<Route> routes() {
        return Collections.emptyList();
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return Arrays.asList(
                new ReplacedRoute(POST, "/{index}" + BASE_URI, POST, "/{index}" + LEGACY_OPENDISTRO_BASE_URI),
                new ReplacedRoute(POST, BASE_URI, POST, LEGACY_OPENDISTRO_BASE_URI)
        );
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        SearchRequest searchRequest = new SearchRequest();

        IntConsumer setSize = size -> searchRequest.source().size(size);
        request.withContentOrSourceParamParserOrNull(parser ->
                RestSearchAction.parseSearchRequest(searchRequest, request, parser, client.getNamedWriteableRegistry(), setSize));
        SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);

        submitAsynchronousSearchRequest.waitForCompletionTimeout(request.paramAsTime("wait_for_completion_timeout",
                SubmitAsynchronousSearchRequest.DEFAULT_WAIT_FOR_COMPLETION_TIMEOUT));

        submitAsynchronousSearchRequest.keepAlive(request.paramAsTime("keep_alive", SubmitAsynchronousSearchRequest.DEFAULT_KEEP_ALIVE));

        submitAsynchronousSearchRequest.keepOnCompletion(request.paramAsBoolean("keep_on_completion",
                SubmitAsynchronousSearchRequest.DEFAULT_KEEP_ON_COMPLETION));

        searchRequest.requestCache(request.paramAsBoolean("request_cache", SubmitAsynchronousSearchRequest.DEFAULT_REQUEST_CACHE));

        searchRequest.setBatchedReduceSize(request.paramAsInt("batched_reduce_size",
                SubmitAsynchronousSearchRequest.DEFAULT_BATCHED_REDUCE_SIZE));

        return channel -> {
            RestCancellableNodeClient cancellableClient = new RestCancellableNodeClient(client, request.getHttpChannel());
            cancellableClient.execute(SubmitAsynchronousSearchAction.INSTANCE, submitAsynchronousSearchRequest,
                    new RestStatusToXContentListener<>(channel));
        };
    }

    @Override
    protected Set<String> responseParams() {
        return RESPONSE_PARAMS;
    }

    @Override
    public boolean allowsUnsafeBuffers() {
        return true;
    }
}
