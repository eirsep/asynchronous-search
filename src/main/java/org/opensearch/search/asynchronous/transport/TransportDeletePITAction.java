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

package org.opensearch.search.asynchronous.transport;

import org.opensearch.action.ActionListener;
import org.opensearch.action.search.ClearScrollController;
import org.opensearch.action.search.SearchContextId;
import org.opensearch.action.search.SearchTransportService;
import org.opensearch.action.search.TransportSearchAction;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.search.SearchPhaseResult;
import org.opensearch.search.SearchService;
import org.opensearch.search.asynchronous.response.DeletePITRequest;
import org.opensearch.search.asynchronous.response.DeletePITResponse;
import org.opensearch.search.internal.ShardSearchContextId;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class TransportDeletePITAction extends HandledTransportAction<DeletePITRequest, DeletePITResponse> {

    private SearchService searchService;
    private final NamedWriteableRegistry namedWriteableRegistry;
    private TransportSearchAction transportSearchAction;
    private final ClusterService clusterService;
    private final SearchTransportService searchTransportService;

    @Inject
    public TransportDeletePITAction(SearchService searchService,
                                    TransportService transportService,
                                    ActionFilters actionFilters,
                                    NamedWriteableRegistry namedWriteableRegistry,
                                    TransportSearchAction transportSearchAction,
                                    ClusterService clusterService, SearchTransportService searchTransportService) {
        super(DeletePITAction.NAME, transportService, actionFilters, DeletePITRequest::new);
        this.searchService = searchService;
        this.namedWriteableRegistry = namedWriteableRegistry;
        this.transportSearchAction = transportSearchAction;
        this.clusterService = clusterService;
        this.searchTransportService = searchTransportService;
    }


    @Override
    protected void doExecute(Task task, DeletePITRequest request, ActionListener<DeletePITResponse> listener) {
        SearchContextId contextId = SearchContextId.decode(namedWriteableRegistry, request.getId());
        ClearScrollController.closeContexts(clusterService.state().nodes(), searchTransportService, contextId.shards().values(),
                ActionListener.wrap(r -> {
                    if (r == contextId.shards().size()) {
                        listener.onResponse(new DeletePITResponse(true));
                    } else {
                        listener.onResponse(new DeletePITResponse(false));
                    }
                }, listener::onFailure));
    }

    public static class PITSinglePhaseSearchResult extends SearchPhaseResult {
        public void setContextId(ShardSearchContextId contextId) {
            this.contextId = contextId;
        }
    }
}

