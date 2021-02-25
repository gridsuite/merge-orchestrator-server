/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.IgmRepository;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigEntity;
import org.gridsuite.merge.orchestrator.server.repositories.ProcessConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class MergeOrchestratorConfigService {

    private final ProcessConfigRepository processConfigRepository;

    private final MergeRepository mergeRepository;

    private final IgmRepository igmRepository;

    public MergeOrchestratorConfigService(ProcessConfigRepository processConfigRepository,
                                          IgmRepository igmRepository,
                                          MergeRepository mergeRepository) {
        this.processConfigRepository = processConfigRepository;
        this.mergeRepository = mergeRepository;
        this.igmRepository = igmRepository;
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
        mergeRepository.deleteByProcess(process);
        igmRepository.deleteByProcess(process);
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcess(), processConfigEntity.getBusinessProcess(), processConfigEntity.getTsos(), processConfigEntity.isRunBalancesAdjustment());
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig) {
        return new ProcessConfigEntity(processConfig.getProcess(), processConfig.getBusinessProcess(), processConfig.getTsos(), processConfig.isRunBalancesAdjustment());
    }
}
