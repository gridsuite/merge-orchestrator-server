/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import java.util.UUID;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * @author Jon harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Table("merges")
public class MergeEntity {

    @PrimaryKey
    private MergeEntityKey key;

    private String status;

    private UUID networkUuid;

    public MergeEntity(MergeEntityKey key, String status, UUID networkUuid) {
        this.key = key;
        this.status = status;
        this.networkUuid = networkUuid;
    }

    public MergeEntityKey getKey() {
        return key;
    }

    public String getStatus() {
        return status;
    }

    public UUID getNetworkUuid() {
        return networkUuid;
    }
}
