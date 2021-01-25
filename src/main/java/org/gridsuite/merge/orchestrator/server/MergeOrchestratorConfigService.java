/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.dto.Tso;
import org.gridsuite.merge.orchestrator.server.repositories.*;
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

    public List<Tso> getTsos() {
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
        mergeRepository.deleteByProcess(process);
        igmRepository.deleteByProcess(process);
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcess(), processConfigEntity.getTsos().stream().map(this::toTso).collect(Collectors.toList()), processConfigEntity.isRunBalancesAdjustment());
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig) {
        return new ProcessConfigEntity(processConfig.getProcess(), processConfig.getTsos().stream().map(this::toTsoEntity).collect(Collectors.toList()), processConfig.isRunBalancesAdjustment());
    }

    private Tso toTso(TsoEntity tsoEntity) {
        return new Tso(tsoEntity.getSourcingActor(), tsoEntity.getAlternativeSourcingActor());
    }

    private TsoEntity toTsoEntity(Tso tso) {
        return new TsoEntity(tso.getSourcingActor(), tso.getAlternativeSourcingActor());
    }
}
