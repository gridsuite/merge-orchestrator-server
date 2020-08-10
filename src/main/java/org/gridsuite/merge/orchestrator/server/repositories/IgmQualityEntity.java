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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Getter
@Table("igms_qualities")
public class IgmQualityEntity {

    @PrimaryKey
    private UUID caseUuid;

    private UUID networkUuid;

    private LocalDateTime date;

    private boolean valid;

    public IgmQualityEntity(UUID caseUuid, UUID networkUuid, LocalDateTime date, boolean valid) {
        this.caseUuid = caseUuid;
        this.networkUuid = networkUuid;
        this.date = date;
        this.valid = valid;
    }
}
