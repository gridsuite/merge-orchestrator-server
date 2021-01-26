/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.repositories;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@UserDefinedType("tso")
@AllArgsConstructor
@Getter
@Builder
@ToString
public class TsoEntity {

    private String sourcingActor;

    private String alternativeSourcingActor;
}
