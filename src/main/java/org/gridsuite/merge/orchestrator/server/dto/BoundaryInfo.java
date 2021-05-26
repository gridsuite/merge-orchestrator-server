/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.dto;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * @author Franck Lecuyer <etienne.homer at rte-france.com>
 */

@AllArgsConstructor
@Getter
@ApiModel("Boundary info")
public class BoundaryInfo {

    private String id;

    private String filename;

    private LocalDateTime scenarioTime;
}
