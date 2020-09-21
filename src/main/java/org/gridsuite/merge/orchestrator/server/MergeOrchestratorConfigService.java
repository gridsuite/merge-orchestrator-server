/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigEntity;
import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@Service
public class MergeOrchestratorConfigService {

    private final ProcessConfigRepository processConfigRepository;

    public MergeOrchestratorConfigService(ProcessConfigRepository processConfigRepository) {
        this.processConfigRepository = processConfigRepository;
    }

    public List<String> getTsos() {
        return getConfigs().stream().flatMap(config -> config.getTsos().stream()).collect(Collectors.toList());
    }

    List<ProcessConfig> getConfigs() {
        return processConfigRepository.findAll().stream().map(this::toProcessConfig).collect(Collectors.toList());
    }

    Optional<ProcessConfig> getConfig(String process) {
        return processConfigRepository.findById(process).map(this::toProcessConfig);
    }

    void addConfig(ProcessConfig processConfig) {
        processConfigRepository.save(toProcessConfigEntity(processConfig));
    }

    public void deleteConfig(String process) {
        processConfigRepository.deleteById(process);
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcess(), processConfigEntity.getTsos(), processConfigEntity.isRunBalancesAdjustment());
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig) {
        return new ProcessConfigEntity(processConfig.getProcess(), processConfig.getTsos(), processConfig.isRunBalancesAdjustment());
    }
}
