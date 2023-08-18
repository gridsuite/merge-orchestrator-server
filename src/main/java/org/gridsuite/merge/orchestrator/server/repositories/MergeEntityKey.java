/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Getter
@EqualsAndHashCode
@ToString
@Embeddable
public class MergeEntityKey implements Serializable {

    private UUID processUuid;

    private LocalDateTime date;

    public MergeEntityKey() {
    }

    public MergeEntityKey(UUID processUuid, LocalDateTime date) {
        this.processUuid = processUuid;
        this.date = date;
    }
}
