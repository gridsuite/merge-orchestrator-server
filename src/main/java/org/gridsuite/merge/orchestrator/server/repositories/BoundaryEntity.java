/*
 *  Copyright (c) 2021, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "boundary")
public class BoundaryEntity extends AbstractManuallyAssignedIdentifierEntity<String> {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "filename")
    private String filename;

    @Column(name = "scenarioTime")
    private LocalDateTime scenarioTime;

    @Override
    public String getId() {
        return id;
    }
}
