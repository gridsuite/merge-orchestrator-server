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
    private IgmEntityKey key;

    private String status;

    private UUID networkUuid;

    private UUID caseUuid;

    public IgmEntity(IgmEntityKey key, String status, UUID networkUuid, UUID caseUuid) {
        this.key = key;
        this.status = status;
        this.networkUuid = networkUuid;
        this.caseUuid = caseUuid;
    }
}
