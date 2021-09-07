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

package org.opensearch.search.asynchronous.commons;

import org.opensearch.search.asynchronous.listener.AsynchronousSearchProgressListener;
import org.opensearch.search.asynchronous.response.AsynchronousSearchResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.BigArrays;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.pipeline.PipelineAggregator;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

import java.util.function.Function;

public abstract class AsynchronousSearchTestCase extends OpenSearchTestCase {

    public static AsynchronousSearchProgressListener mockAsynchronousSearchProgressListener(ThreadPool threadPool) {
        return new AsynchronousSearchProgressListener(threadPool.absoluteTimeInMillis(), r -> null, e -> null, threadPool.generic(),
                threadPool::relativeTimeInMillis, () -> getReduceContextBuilder(), new Object());
    }

    public static AsynchronousSearchProgressListener mockAsynchronousSearchProgressListener(ThreadPool threadPool,
                                                                       Function<SearchResponse, AsynchronousSearchResponse> successFunction,
                                                                       Function<Exception, AsynchronousSearchResponse> failureFunction) {
        return new AsynchronousSearchProgressListener(threadPool.absoluteTimeInMillis(), successFunction, failureFunction,
                threadPool.generic(),
                threadPool::relativeTimeInMillis, () -> getReduceContextBuilder(), new Object());
    }

    private static InternalAggregation.ReduceContextBuilder getReduceContextBuilder() {
        return new InternalAggregation.ReduceContextBuilder() {
            @Override
            public InternalAggregation.ReduceContext forPartialReduction() {
                return InternalAggregation.ReduceContext.forPartialReduction(BigArrays.NON_RECYCLING_INSTANCE, null,
                        null);
            }

            public InternalAggregation.ReduceContext forFinalReduction() {
                return InternalAggregation.ReduceContext.forFinalReduction(
                        BigArrays.NON_RECYCLING_INSTANCE, null, b -> {}, PipelineAggregator.PipelineTree.EMPTY);
            }};
    }
}
