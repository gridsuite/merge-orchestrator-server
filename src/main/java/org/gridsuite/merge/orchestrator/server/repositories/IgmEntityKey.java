/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Getter
@EqualsAndHashCode
public class IgmEntityKey implements Serializable {

    private UUID processUuid;

    private LocalDateTime date;

    private String tso;

    public IgmEntityKey() {
    }

    public IgmEntityKey(UUID processUuid, LocalDateTime date, String tso) {
        this.processUuid = processUuid;
        this.date = date;
        this.tso = tso;
    }
}
