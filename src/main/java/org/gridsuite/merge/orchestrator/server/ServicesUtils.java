/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
public final class ServicesUtils {

    public static final String DELIMITER = "/";

    private ServicesUtils() {

    }

    public static String getStringUri(List<UUID> networksIds, String delimiter, String balanceAdjustementApiVersion) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(delimiter + balanceAdjustementApiVersion + "/networks/{networkUuid}/run");
        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        return uriBuilder.buildAndExpand(networksIds.get(0).toString()).toUriString();
    }
}
