/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@Service
public class MergeOrchestratorConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorConfigService.class);

    @Value("${parameters.tsos}")
    private String mergeTsos;

    @Value("${parameters.process}")
    private String process;

    @Value("${parameters.run-balances-adjustment}")
    private boolean runBalancesAdjustment;

    @PostConstruct
    public void logParameters() {
        LOGGER.info("TSOs to merge: {}", getTsos());
        LOGGER.info("Process: {}", process);
        LOGGER.info("Run balance adjustment: {}", runBalancesAdjustment);
    }

    public List<String> getTsos() {
        return mergeTsos != null ? Arrays.asList(mergeTsos.split(",")) : Collections.emptyList();
    }

    public String getProcess() {
        return process;
    }

    public boolean isRunBalancesAdjustment() {
        return runBalancesAdjustment;
    }
}
