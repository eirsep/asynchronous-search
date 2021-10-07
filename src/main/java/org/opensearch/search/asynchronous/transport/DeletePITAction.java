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

import org.opensearch.action.ActionType;
import org.opensearch.search.asynchronous.response.DeletePITResponse;

public class DeletePITAction extends ActionType<DeletePITResponse> {

    public static final DeletePITAction INSTANCE = new DeletePITAction();
    public static final String NAME = "indices:data/delete/pit";

    private DeletePITAction() {
        super(NAME, DeletePITResponse::new);
    }
}

