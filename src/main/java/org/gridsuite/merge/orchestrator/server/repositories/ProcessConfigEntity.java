/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@NoArgsConstructor
@Getter
@Entity
@Table(name = "configs")
public class ProcessConfigEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    @Column(name = "processUuid")
    private UUID processUuid;

    @Column(name = "process")
    private String process;

    @Column(name = "businessProcess")
    private String businessProcess;

    @Column(name = "tso")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "processConfigEntity_tsos_fk"), indexes = {@Index(name = "processConfigEntity_tsos_idx", columnList = "ProcessConfigEntity_processUuid")})
    private List<String> tsos;

    @Column(name = "runBalancesAdjustment")
    private boolean runBalancesAdjustment;

    @Column(name = "useLastBoundarySet")
    private boolean useLastBoundarySet;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "eqBoundary_id_fk_constraint"))
    BoundaryEntity eqBoundary;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "tpBoundary_id_fk_constraint"))
    BoundaryEntity tpBoundary;

    public ProcessConfigEntity(UUID processUuid, String process, String businessProcess, List<String> tsos, boolean runBalancesAdjustment,
                               boolean useLastBoundarySet, BoundaryEntity eqBoundary, BoundaryEntity tpBoundary) {
        this.processUuid = processUuid;
        this.process = process;
        this.businessProcess = businessProcess;
        this.tsos = tsos;
        this.runBalancesAdjustment = runBalancesAdjustment;
        this.useLastBoundarySet = useLastBoundarySet;
        this.eqBoundary = eqBoundary;
        this.tpBoundary = tpBoundary;
    }

    @Override
    public UUID getId() {
        return processUuid;
    }
}
