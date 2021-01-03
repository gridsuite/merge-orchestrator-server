/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.merge.orchestrator.server.repositories.TsoEntity;

import java.util.List;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProcessConfig {

    private String process;

    private List<TsoEntity> tsos;

    private boolean runBalancesAdjustment;
}
