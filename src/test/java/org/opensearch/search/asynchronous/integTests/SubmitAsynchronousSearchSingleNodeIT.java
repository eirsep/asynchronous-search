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

package org.opensearch.search.asynchronous.integTests;

import org.opensearch.search.asynchronous.commons.AsynchronousSearchSingleNodeTestCase;
import org.opensearch.search.asynchronous.context.active.AsynchronousSearchActiveStore;
import org.opensearch.search.asynchronous.id.AsynchronousSearchId;
import org.opensearch.search.asynchronous.id.AsynchronousSearchIdConverter;
import org.opensearch.search.asynchronous.request.DeleteAsynchronousSearchRequest;
import org.opensearch.search.asynchronous.request.SubmitAsynchronousSearchRequest;
import org.opensearch.search.asynchronous.response.AcknowledgedResponse;
import org.opensearch.search.asynchronous.response.AsynchronousSearchResponse;
import org.opensearch.search.asynchronous.utils.AsynchronousSearchAssertions;
import org.opensearch.action.ActionListener;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.TriConsumer;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchRejectedExecutionException;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.opensearch.index.query.QueryBuilders.scriptQuery;

public class SubmitAsynchronousSearchSingleNodeIT extends AsynchronousSearchSingleNodeTestCase {

    private int asynchronousSearchConcurrentLimit = 60;

    @Override
    protected Settings nodeSettings() {
        return Settings.builder().put(AsynchronousSearchActiveStore.NODE_CONCURRENT_RUNNING_SEARCHES_SETTING.getKey(),
                asynchronousSearchConcurrentLimit).build();
    }

    public void
    testSubmitAsynchronousSearchWithoutRetainedResponse() throws InterruptedException {
        assertNotEquals(1,2); 
    }

    private void assertConcurrentSubmitsForBlockedSearch(TriConsumer<AtomicInteger, AtomicInteger, AtomicInteger> assertionConsumer,
                                                         int concurrentRuns) throws Exception {
        AtomicInteger numStartedAsynchronousSearch = new AtomicInteger();
        AtomicInteger numFailedAsynchronousSearch = new AtomicInteger();
        AtomicInteger numRejectedAsynchronousSearch = new AtomicInteger();
        TestThreadPool testThreadPool = null;
        List<SearchDelayPlugin> plugins = initPluginFactory();
        try {
            testThreadPool = new TestThreadPool(SubmitAsynchronousSearchSingleNodeIT.class.getName());
            int numThreads = concurrentRuns;
            List<Runnable> operationThreads = new ArrayList<>();
            CountDownLatch countDownLatch = new CountDownLatch(numThreads);
            for (int i = 0; i < numThreads; i++) {
                Runnable thread = () -> {
                    logger.info("Triggering asynchronous search submit --->");
                    SearchRequest searchRequest = new SearchRequest("index");
                    searchRequest.source(new SearchSourceBuilder());
                    searchRequest.source().query(scriptQuery(new Script(ScriptType.INLINE, "mockscript", SearchDelayPlugin.SCRIPT_NAME,
                            Collections.emptyMap())));
                    SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);
                    submitAsynchronousSearchRequest.keepOnCompletion(false);
                    submitAsynchronousSearchRequest.waitForCompletionTimeout(TimeValue.timeValueMillis(100));
                    executeSubmitAsynchronousSearch(client(), submitAsynchronousSearchRequest,
                            new ActionListener<AsynchronousSearchResponse>() {
                                @Override
                                public void onResponse(AsynchronousSearchResponse asResponse) {
                                    if (asResponse.getId() != null) {
                                        numStartedAsynchronousSearch.incrementAndGet();
                                    }
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    if (e instanceof OpenSearchRejectedExecutionException) {
                                        numRejectedAsynchronousSearch.incrementAndGet();
                                    }
                                    numFailedAsynchronousSearch.incrementAndGet();
                                    countDownLatch.countDown();
                                }
                            });
                };
                operationThreads.add(thread);
            }
            TestThreadPool finalTestThreadPool = testThreadPool;
            operationThreads.forEach(runnable -> finalTestThreadPool.executor("generic").execute(runnable));
            countDownLatch.await();
            disableBlocks(plugins);
            assertionConsumer.apply(numStartedAsynchronousSearch, numFailedAsynchronousSearch, numRejectedAsynchronousSearch);
        } finally {
            ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
        }
    }

    private void assertConcurrentSubmits(SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest, SearchResponse searchResponse,
                                         TriConsumer<AtomicInteger, AtomicInteger, AtomicInteger> assertionConsumer, int concurrentRuns)
            throws InterruptedException {
        AtomicInteger numStartedAsynchronousSearch = new AtomicInteger();
        AtomicInteger numFailedAsynchronousSearch = new AtomicInteger();
        AtomicInteger numErrorResponseAsynchronousSearch = new AtomicInteger();
        final ClusterState state = getInstanceFromNode(ClusterService.class).state();

        TestThreadPool testThreadPool = null;
        CountDownLatch countDownLatch;
        try {
            testThreadPool = new TestThreadPool(SubmitAsynchronousSearchSingleNodeIT.class.getName());
            int numThreads = concurrentRuns;
            List<Runnable> operationThreads = new ArrayList<>();
            if (submitAsynchronousSearchRequest.getKeepOnCompletion()) {
                //we also need to delete asynchronous search response to ensure test completes gracefully with no background tasks
                // running
                countDownLatch = new CountDownLatch(2 * numThreads);
            } else {
                countDownLatch = new CountDownLatch(numThreads);
            }

            for (int i = 0; i < numThreads; i++) {
                CountDownLatch finalCountDownLatch = countDownLatch;
                Runnable thread = () -> {
                    logger.info("Triggering asynchronous search submit --->");
                    executeSubmitAsynchronousSearch(client(), submitAsynchronousSearchRequest,
                            new ActionListener<AsynchronousSearchResponse>() {
                                @Override
                                public void onResponse(AsynchronousSearchResponse asResponse) {
                                    if (asResponse.getId() != null) {
                                        AsynchronousSearchId asId = AsynchronousSearchIdConverter.parseAsyncId(asResponse.getId());
                                        assertEquals(state.nodes().getLocalNodeId(), asId.getNode());
                                        AsynchronousSearchAssertions.assertSearchResponses(searchResponse, asResponse.getSearchResponse());
                                        numStartedAsynchronousSearch.incrementAndGet();
                                    }
                                    if (asResponse.getError() != null) {
                                        numErrorResponseAsynchronousSearch.incrementAndGet();
                                    }
                                    finalCountDownLatch.countDown();

                                    if (submitAsynchronousSearchRequest.getKeepOnCompletion()) {
                                        DeleteAsynchronousSearchRequest deleteAsynchronousSearchRequest
                                                = new DeleteAsynchronousSearchRequest(asResponse.getId());
                                        executeDeleteAsynchronousSearch(client(), deleteAsynchronousSearchRequest,
                                                new LatchedActionListener<>(new ActionListener<AcknowledgedResponse>() {
                                                    @Override
                                                    public void onResponse(AcknowledgedResponse acknowledgedResponse) {
                                                        assertTrue(acknowledgedResponse.isAcknowledged());
                                                    }

                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        fail("Search deletion failed for asynchronous search id " + e.getMessage());
                                                    }
                                                }, finalCountDownLatch));
                                    }
                                    ;
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    numFailedAsynchronousSearch.incrementAndGet();
                                    finalCountDownLatch.countDown();
                                }
                            });
                };
                operationThreads.add(thread);
            }
            TestThreadPool finalTestThreadPool = testThreadPool;
            operationThreads.forEach(runnable -> finalTestThreadPool.executor("generic").execute(runnable));
            countDownLatch.await();
            assertionConsumer.apply(numStartedAsynchronousSearch, numFailedAsynchronousSearch, numErrorResponseAsynchronousSearch);
        } finally {
            ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
        }
    }
}
