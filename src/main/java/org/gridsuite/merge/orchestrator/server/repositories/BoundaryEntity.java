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

import java.nio.ByteBuffer;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Getter
@Table("boundaries")
public class BoundaryEntity {

    @PrimaryKey
    private String id;

    private ByteBuffer boundary;

    private String filename;

    public BoundaryEntity(String id, ByteBuffer boundary, String filename) {
        this.id = id;
        this.boundary = boundary;
        this.filename = filename;
    }
}
