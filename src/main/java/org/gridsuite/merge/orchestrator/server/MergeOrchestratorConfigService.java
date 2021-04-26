/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
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

    private final NetworkStoreService networkStoreService;

    public MergeOrchestratorConfigService(ProcessConfigRepository processConfigRepository,
                                          IgmRepository igmRepository,
                                          MergeRepository mergeRepository,
                                          NetworkStoreService networkStoreService) {
        this.processConfigRepository = processConfigRepository;
        this.mergeRepository = mergeRepository;
        this.igmRepository = igmRepository;
        this.networkStoreService = networkStoreService;
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
        igmRepository.findByProcess(process).stream()
                .map(IgmEntity::getNetworkUuid)
                .filter(Objects::nonNull)
                .forEach(networkStoreService::deleteNetwork);
        igmRepository.deleteByProcess(process);
        mergeRepository.deleteByProcess(process);
        processConfigRepository.deleteById(process);
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcess(), processConfigEntity.getBusinessProcess(), processConfigEntity.getTsos(), processConfigEntity.isRunBalancesAdjustment());
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig) {
        return new ProcessConfigEntity(processConfig.getProcess(), processConfig.getBusinessProcess(), processConfig.getTsos(), processConfig.isRunBalancesAdjustment());
    }
}
