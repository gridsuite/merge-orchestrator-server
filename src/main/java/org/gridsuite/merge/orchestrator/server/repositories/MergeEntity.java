/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.Getter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Jon harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Entity
@Getter
@ToString
@Table(name = "merge")
@IdClass(MergeEntityKey.class)
public class MergeEntity implements Serializable {

    @Id
    @Column(name = "processUuid")
    private UUID processUuid;

    @Id
    @Column(name = "date")
    private LocalDateTime date;

    @Column(name = "status")
    private String status;

    public MergeEntity() {
    }

    public MergeEntity(MergeEntityKey key, String status) {
        this.processUuid = key.getProcessUuid();
        this.date = key.getDate();
        this.status = status;
    }
}
