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
import org.opensearch.search.asynchronous.response.PITResponse;

public class CreatePITAction extends ActionType<PITResponse> {

    public static final CreatePITAction INSTANCE = new CreatePITAction();
    public static final String NAME = "indices:data/create/pit";

    private CreatePITAction() {
        super(NAME, PITResponse::new);
    }
}
