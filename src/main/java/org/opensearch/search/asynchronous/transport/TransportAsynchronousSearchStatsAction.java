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

package org.opensearch.search.asynchronous.transport;

import org.opensearch.search.asynchronous.action.AsynchronousSearchStatsAction;
import org.opensearch.search.asynchronous.request.AsynchronousSearchStatsRequest;
import org.opensearch.search.asynchronous.response.AsynchronousSearchStatsResponse;
import org.opensearch.search.asynchronous.service.AsynchronousSearchService;
import org.opensearch.search.asynchronous.stats.AsynchronousSearchStats;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.List;

public class TransportAsynchronousSearchStatsAction
        extends TransportNodesAction<AsynchronousSearchStatsRequest, AsynchronousSearchStatsResponse,
        TransportAsynchronousSearchStatsAction.AsynchronousSearchStatsNodeRequest, AsynchronousSearchStats> {

    private final AsynchronousSearchService asynchronousSearchService;

    @Inject
    public TransportAsynchronousSearchStatsAction(ThreadPool threadPool, ClusterService clusterService, TransportService transportService,
                                           ActionFilters actionFilters, AsynchronousSearchService asynchronousSearchService) {

        super(AsynchronousSearchStatsAction.NAME, threadPool, clusterService, transportService, actionFilters,
                AsynchronousSearchStatsRequest::new, AsynchronousSearchStatsNodeRequest::new, ThreadPool.Names.MANAGEMENT,
                AsynchronousSearchStats.class);
        this.asynchronousSearchService = asynchronousSearchService;

    }

    @Override
    protected AsynchronousSearchStatsResponse newResponse(AsynchronousSearchStatsRequest request, List<AsynchronousSearchStats> responses,
                                                   List<FailedNodeException> failures) {
        return new AsynchronousSearchStatsResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected AsynchronousSearchStatsNodeRequest newNodeRequest(AsynchronousSearchStatsRequest request) {
        return new AsynchronousSearchStatsNodeRequest(request);
    }

    @Override
    protected AsynchronousSearchStats newNodeResponse(StreamInput in) throws IOException {
        return new AsynchronousSearchStats(in);
    }

    @Override
    protected AsynchronousSearchStats nodeOperation(AsynchronousSearchStatsNodeRequest asynchronousSearchStatsNodeRequest) {
        return asynchronousSearchService.stats();

    }

    /**
     * Request to fetch asynchronous search stats on a single node
     */
    public static class AsynchronousSearchStatsNodeRequest extends BaseNodeRequest {

        AsynchronousSearchStatsRequest request;

        public AsynchronousSearchStatsNodeRequest(StreamInput in) throws IOException {
            super(in);
            request = new AsynchronousSearchStatsRequest(in);
        }

        AsynchronousSearchStatsNodeRequest(AsynchronousSearchStatsRequest request) {
            this.request = request;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            request.writeTo(out);
        }
    }
}
