/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfo;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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

    @Transactional(readOnly = true)
    public List<ProcessConfig> getConfigs() {
        return processConfigRepository.findAll().stream().map(entity -> {
            @SuppressWarnings("unused")
            int ignoreSize = entity.getTsos().size();
            return entity;
        }).map(this::toProcessConfig).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ProcessConfig> getConfig(UUID processUuid) {
        return processConfigRepository.findById(processUuid).map(entity -> {
            @SuppressWarnings("unused")
            int ignoreSize = entity.getTsos().size();
            return entity;
        }).map(this::toProcessConfig);
    }

    void addConfig(ProcessConfig processConfig) {
        processConfigRepository.save(toProcessConfigEntity(processConfig));
    }

    @Transactional
    public void deleteConfig(UUID processUuid) {
        igmRepository.findByKeyProcessUuid(processUuid).stream()
                .filter(Objects::nonNull)
                .map(IgmEntity::getNetworkUuid)
                .forEach(networkStoreService::deleteNetwork);
        igmRepository.deleteByKeyProcessUuid(processUuid);
        mergeRepository.deleteByKeyProcessUuid(processUuid);
        processConfigRepository.deleteById(processUuid);
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcessUuid(), processConfigEntity.getProcess(),
            processConfigEntity.getBusinessProcess(), processConfigEntity.getTsos(),
            processConfigEntity.isRunBalancesAdjustment(),
            processConfigEntity.isUseLastBoundarySet(),
            toBoundaryInfo(processConfigEntity.getEqBoundary()),
            toBoundaryInfo(processConfigEntity.getTpBoundary()));
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig) {
        return new ProcessConfigEntity(processConfig.getProcessUuid() == null ? UUID.randomUUID() : processConfig.getProcessUuid(),
            processConfig.getProcess(), processConfig.getBusinessProcess(), processConfig.getTsos(),
            processConfig.isRunBalancesAdjustment(),
            processConfig.isUseLastBoundarySet(),
            toBoundaryEntity(processConfig.getEqBoundary()),
            toBoundaryEntity(processConfig.getTpBoundary()));
    }

    private BoundaryEntity toBoundaryEntity(BoundaryInfo boundary) {
        return boundary != null ? new BoundaryEntity(boundary.getId(), boundary.getFilename(), boundary.getScenarioTime()) : null;
    }

    private BoundaryInfo toBoundaryInfo(BoundaryEntity boundary) {
        return boundary != null ? new BoundaryInfo(boundary.getId(), boundary.getFilename(), boundary.getScenarioTime()) : null;
    }
}
