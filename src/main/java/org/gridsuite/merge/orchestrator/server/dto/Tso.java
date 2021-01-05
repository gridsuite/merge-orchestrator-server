/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.dto;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.gridsuite.merge.orchestrator.server.repositories.TsoEntity;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@AllArgsConstructor
@Getter
@Builder
@ToString
@ApiModel("Tso")
public class Tso {
    private String sourcingActor;
    private String alternativeSourcingActor;

    public Tso(TsoEntity tsoEntity) {
        this.sourcingActor = tsoEntity.getSourcingActor();
        this.alternativeSourcingActor = tsoEntity.getAlternativeSourcingActor();
    }
}
