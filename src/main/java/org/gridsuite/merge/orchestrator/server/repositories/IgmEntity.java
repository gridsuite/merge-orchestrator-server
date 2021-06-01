/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Entity
@Getter
@Setter
@ToString
@Table(name = "merge_igm")
public class IgmEntity {

    @EmbeddedId
    private IgmEntityKey key;

    @Column(name = "status")
    private String status;

    @Column(name = "networkUuid")
    private UUID networkUuid;

    @Column(name = "caseUuid")
    private UUID caseUuid;

    @Column(name = "replacingDate")
    private LocalDateTime replacingDate;

    @Column(name = "replacingBusinessProcess")
    private String replacingBusinessProcess;

    @Column(name = "eqBoundary")
    private String eqBoundary;

    @Column(name = "tpBoundary")
    private String tpBoundary;

    public IgmEntity() {
    }

    public IgmEntity(IgmEntityKey key, String status, UUID networkUuid, UUID caseUuid,
                     LocalDateTime replacingDate, String replacingBusinessProcess,
                     String eqBoundary, String tpBoundary) {
        this.key = key;
        this.status = status;
        this.networkUuid = networkUuid;
        this.caseUuid = caseUuid;
        this.replacingDate = replacingDate;
        this.replacingBusinessProcess = replacingBusinessProcess;
        this.eqBoundary = eqBoundary;
        this.tpBoundary = tpBoundary;
    }
}
