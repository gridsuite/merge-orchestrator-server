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
import java.util.UUID;

/**
 * @author Jon harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Entity
@Getter
@Setter
@ToString
@Table(name = "merge")
public class MergeEntity {

    @EmbeddedId
    private MergeEntityKey key;

    @Column(name = "status")
    private String status;

    @Column(name = "report")
    private UUID reportUUID;

    public MergeEntity() {
    }

    public MergeEntity(MergeEntityKey key, String status) {
        this.key = key;
        this.status = status;
        this.reportUUID = UUID.randomUUID();
    }
}
