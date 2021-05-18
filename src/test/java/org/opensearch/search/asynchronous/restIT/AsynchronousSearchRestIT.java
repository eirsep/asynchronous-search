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

package org.opensearch.search.asynchronous.restIT;

import org.opensearch.search.asynchronous.context.state.AsynchronousSearchState;
import org.opensearch.search.asynchronous.request.DeleteAsynchronousSearchRequest;
import org.opensearch.search.asynchronous.request.GetAsynchronousSearchRequest;
import org.opensearch.search.asynchronous.request.SubmitAsynchronousSearchRequest;
import org.opensearch.search.asynchronous.response.AsynchronousSearchResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.ResponseException;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.search.asynchronous.utils.TestUtils;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.apache.lucene.util.LuceneTestCase.expectThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AsynchronousSearchRestIT extends AsynchronousSearchRestTestCase {

    public void testSubmitWithoutRetainedResponse() throws IOException {
        SearchRequest searchRequest = new SearchRequest("test");
        searchRequest.source(new SearchSourceBuilder());
        SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);
        submitAsynchronousSearchRequest.keepOnCompletion(false);
        submitAsynchronousSearchRequest.waitForCompletionTimeout(TimeValue.timeValueMillis(randomLongBetween(1, 500)));
        AsynchronousSearchResponse submitResponse = executeSubmitAsynchronousSearch(submitAsynchronousSearchRequest);
        List<AsynchronousSearchState> legalStates = Arrays.asList(
                AsynchronousSearchState.RUNNING, AsynchronousSearchState.SUCCEEDED, AsynchronousSearchState.CLOSED);
        assertTrue(legalStates.contains(submitResponse.getState()));
        GetAsynchronousSearchRequest getAsynchronousSearchRequest = new GetAsynchronousSearchRequest(submitResponse.getId());
        AsynchronousSearchResponse getResponse;
        do {
            getResponse = null;
            try {
                getResponse = getAssertedAsynchronousSearchResponse(submitResponse, getAsynchronousSearchRequest);
                if (AsynchronousSearchState.SUCCEEDED.equals(getResponse.getState())
                        || AsynchronousSearchState.CLOSED.equals(getResponse.getState())) {
                    assertNotNull(getResponse.getSearchResponse());
                    assertHitCount(getResponse.getSearchResponse(), 5L);
                }
            } catch (Exception e) {
                assertRnf(e);
            }
        } while (getResponse != null && legalStates.contains(getResponse.getState()));
    }

    public void testSubmitWithRetainedResponse() throws IOException {
        SearchRequest searchRequest = new SearchRequest("test");
        searchRequest.source(new SearchSourceBuilder());
        SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);
        submitAsynchronousSearchRequest.keepOnCompletion(true);
        submitAsynchronousSearchRequest.waitForCompletionTimeout(TimeValue.timeValueMillis(randomLongBetween(1, 500)));
        AsynchronousSearchResponse submitResponse = executeSubmitAsynchronousSearch(submitAsynchronousSearchRequest);
        List<AsynchronousSearchState> legalStates = Arrays.asList(
                AsynchronousSearchState.RUNNING, AsynchronousSearchState.SUCCEEDED, AsynchronousSearchState.PERSIST_SUCCEEDED,
                AsynchronousSearchState.PERSISTING,
                AsynchronousSearchState.CLOSED, AsynchronousSearchState.STORE_RESIDENT);
        assertNotNull(submitResponse.getId());
        assertTrue(submitResponse.getState().name(), legalStates.contains(submitResponse.getState()));
        GetAsynchronousSearchRequest getAsynchronousSearchRequest = new GetAsynchronousSearchRequest(submitResponse.getId());
        AsynchronousSearchResponse getResponse;
        do {
            getResponse = getAssertedAsynchronousSearchResponse(submitResponse, getAsynchronousSearchRequest);
            if (getResponse.getState() == AsynchronousSearchState.RUNNING && getResponse.getSearchResponse() != null) {
                assertEquals(getResponse.getSearchResponse().getHits().getHits().length, 0);
            } else {
                assertNotNull(getResponse.getSearchResponse());
                assertNotEquals(getResponse.getSearchResponse().getTook(), -1L);
            }
        } while (AsynchronousSearchState.STORE_RESIDENT.equals(getResponse.getState()) == false);
        getResponse = getAssertedAsynchronousSearchResponse(submitResponse, getAsynchronousSearchRequest);
        assertNotNull(getResponse.getSearchResponse());
        assertEquals(AsynchronousSearchState.STORE_RESIDENT, getResponse.getState());
        assertHitCount(getResponse.getSearchResponse(), 5);
        executeDeleteAsynchronousSearch(new DeleteAsynchronousSearchRequest(submitResponse.getId()));
    }

    public void testSubmitSearchCompletesBeforeWaitForCompletionTimeout() throws IOException {
        SearchRequest searchRequest = new SearchRequest("test");
        searchRequest.source(new SearchSourceBuilder());
        SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);
        submitAsynchronousSearchRequest.keepOnCompletion(true);
        submitAsynchronousSearchRequest.keepAlive(TimeValue.timeValueHours(5));
        submitAsynchronousSearchRequest.waitForCompletionTimeout(TimeValue.timeValueMinutes(1));
        AsynchronousSearchResponse submitResponse = executeSubmitAsynchronousSearch(submitAsynchronousSearchRequest);
        List<AsynchronousSearchState> legalStates = Arrays.asList(AsynchronousSearchState.SUCCEEDED,
                AsynchronousSearchState.PERSIST_SUCCEEDED,
                AsynchronousSearchState.PERSISTING, AsynchronousSearchState.CLOSED, AsynchronousSearchState.STORE_RESIDENT);
        assertTrue(submitResponse.getState().name(), legalStates.contains(submitResponse.getState()));
        assertHitCount(submitResponse.getSearchResponse(), 5L);
        GetAsynchronousSearchRequest getAsynchronousSearchRequest = new GetAsynchronousSearchRequest(submitResponse.getId());
        AsynchronousSearchResponse getResponse = getAssertedAsynchronousSearchResponse(submitResponse, getAsynchronousSearchRequest);
        assertEquals(TestUtils.getResponseAsMap(getResponse.getSearchResponse()), TestUtils.getResponseAsMap(submitResponse.getSearchResponse()));
        executeDeleteAsynchronousSearch(new DeleteAsynchronousSearchRequest(submitResponse.getId()));
    }

    public void testGetWithoutKeepAliveUpdate() throws IOException {
        SearchRequest searchRequest = new SearchRequest("test");
        searchRequest.source(new SearchSourceBuilder());
        SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);
        submitAsynchronousSearchRequest.keepOnCompletion(true);
        AsynchronousSearchResponse submitResponse = executeSubmitAsynchronousSearch(submitAsynchronousSearchRequest);
        AsynchronousSearchResponse getResponse = executeGetAsynchronousSearch(new GetAsynchronousSearchRequest(submitResponse.getId()));
        assertEquals(getResponse.getExpirationTimeMillis(), submitResponse.getExpirationTimeMillis());
        executeDeleteAsynchronousSearch(new DeleteAsynchronousSearchRequest(submitResponse.getId()));
        ResponseException responseException = expectThrows(ResponseException.class, () -> executeGetAsynchronousSearch(
                new GetAsynchronousSearchRequest(submitResponse.getId())));
        assertRnf(responseException);
    }

    public void testGetWithKeepAliveUpdate() throws IOException {
        SearchRequest searchRequest = new SearchRequest("test");
        TimeValue keepAlive = TimeValue.timeValueHours(5);
        searchRequest.source(new SearchSourceBuilder());
        SubmitAsynchronousSearchRequest submitAsynchronousSearchRequest = new SubmitAsynchronousSearchRequest(searchRequest);
        submitAsynchronousSearchRequest.keepOnCompletion(true);
        submitAsynchronousSearchRequest.keepAlive(keepAlive);
        AsynchronousSearchResponse submitResponse = executeSubmitAsynchronousSearch(submitAsynchronousSearchRequest);
        GetAsynchronousSearchRequest getAsynchronousSearchRequest = new GetAsynchronousSearchRequest(submitResponse.getId());
        getAsynchronousSearchRequest.setKeepAlive(keepAlive);
        AsynchronousSearchResponse getResponse = executeGetAsynchronousSearch(getAsynchronousSearchRequest);
        assertThat(getResponse.getExpirationTimeMillis(), greaterThan(submitResponse.getExpirationTimeMillis()));
        executeDeleteAsynchronousSearch(new DeleteAsynchronousSearchRequest(submitResponse.getId()));
        ResponseException responseException = expectThrows(ResponseException.class, () -> executeGetAsynchronousSearch(
                new GetAsynchronousSearchRequest(submitResponse.getId())));
        assertRnf(responseException);
    }
}
