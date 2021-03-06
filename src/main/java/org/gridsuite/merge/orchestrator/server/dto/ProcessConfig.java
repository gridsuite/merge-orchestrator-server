/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.dto;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("Process config")
public class ProcessConfig {

    public static final String ACCEPTED_FORMAT = "CGMES";

    private String process;

    private String businessProcess;

    private List<Tso> tsos;

    private boolean runBalancesAdjustment;

    private boolean isMatching(String tso) {
        return this.getTsos().stream().anyMatch(ts -> ts.isMatching(tso));
    }

    private boolean isMatching(String tso, String businessProcess) {
        return this.isMatching(tso) && this.getBusinessProcess().equals(businessProcess);
    }

    public boolean isMatching(String tso, String format, String businessProcess) {
        return StringUtils.isNotEmpty(tso) && StringUtils.isNotEmpty(format) && StringUtils.isNotEmpty(businessProcess) &&
                StringUtils.equals(format, ACCEPTED_FORMAT) && this.isMatching(tso, businessProcess);
    }
}
