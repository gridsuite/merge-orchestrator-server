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

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Entity
@Getter
@Setter
@ToString
@Table(name = "merge_igm")
@IdClass(IgmEntityKey.class)
public class IgmEntity {

    @Id
    @Column(name = "processUuid")
    private UUID processUuid;

    @Id
    @Column(name = "date")
    private LocalDateTime date;

    @Id
    @Column(name = "tso")
    private String tso;

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

    @Column(name = "boundary")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "igmEntity_boundaries_fk"), indexes = {@Index(name = "igmEntity_boundaries_idx", columnList = "IgmEntity_processUuid, IgmEntity_date, IgmEntity_tso")})
    private List<UUID> boundaries;

    public IgmEntity() {
    }

    public IgmEntity(IgmEntityKey key, String status, UUID networkUuid, UUID caseUuid,
                     LocalDateTime replacingDate, String replacingBusinessProcess,
                     List<UUID> boundaries) {
        this.processUuid = key.getProcessUuid();
        this.date = key.getDate();
        this.tso = key.getTso();
        this.status = status;
        this.networkUuid = networkUuid;
        this.caseUuid = caseUuid;
        this.replacingDate = replacingDate;
        this.replacingBusinessProcess = replacingBusinessProcess;
        this.boundaries = boundaries;
    }
}
