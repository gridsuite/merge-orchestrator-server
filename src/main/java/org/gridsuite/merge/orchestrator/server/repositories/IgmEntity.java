/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.Getter;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Getter
@Table("merge_igm")
public class IgmEntity {

    @PrimaryKey
    private MergeEntityKey key;

    private String tso;

    private String status;

    private UUID networkUuid;

    public IgmEntity(MergeEntityKey key, String tso, String status, UUID networkUuid) {
        this.key = key;
        this.tso = tso;
        this.status = status;
        this.networkUuid = networkUuid;
    }
}
