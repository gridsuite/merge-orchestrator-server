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

    private final BoundaryRepository boundaryRepository;

    private final MergeRepository mergeRepository;

    private final IgmRepository igmRepository;

    private final NetworkStoreService networkStoreService;

    public MergeOrchestratorConfigService(ProcessConfigRepository processConfigRepository,
                                          BoundaryRepository boundaryRepository,
                                          IgmRepository igmRepository,
                                          MergeRepository mergeRepository,
                                          NetworkStoreService networkStoreService) {
        this.processConfigRepository = processConfigRepository;
        this.boundaryRepository = boundaryRepository;
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

    @Transactional
    void addConfig(ProcessConfig processConfig) {
        ProcessConfigEntity entity = toProcessConfigEntity(processConfig);
        processConfigRepository.save(entity);
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
        Optional<BoundaryEntity> eqBoundary = Optional.empty();
        Optional<BoundaryEntity> tpBoundary = Optional.empty();

        if (processConfig.getEqBoundary() != null) {
            eqBoundary = boundaryRepository.findById(processConfig.getEqBoundary().getId()).map(e -> {
                e.markNotNew();
                return e;
            });
        }
        if (processConfig.getTpBoundary() != null) {
            tpBoundary = boundaryRepository.findById(processConfig.getTpBoundary().getId()).map(e -> {
                e.markNotNew();
                return e;
            });
        }

        boolean isNewProcessConfig = processConfig.getProcessUuid() == null;
        ProcessConfigEntity entity = new ProcessConfigEntity(isNewProcessConfig ? UUID.randomUUID() : processConfig.getProcessUuid(),
            processConfig.getProcess(), processConfig.getBusinessProcess(), processConfig.getTsos(),
            processConfig.isRunBalancesAdjustment(),
            processConfig.isUseLastBoundarySet(),
            eqBoundary.orElse(toBoundaryEntity(processConfig.getEqBoundary())),
            tpBoundary.orElse(toBoundaryEntity(processConfig.getTpBoundary())));
        if (!isNewProcessConfig) {
            entity.markNotNew();
        }
        return entity;
    }

    private BoundaryEntity toBoundaryEntity(BoundaryInfo boundary) {
        return boundary != null ? new BoundaryEntity(boundary.getId(), boundary.getFilename(), boundary.getScenarioTime()) : null;
    }

    private BoundaryInfo toBoundaryInfo(BoundaryEntity boundary) {
        return boundary != null ? new BoundaryInfo(boundary.getId(), boundary.getFilename(), boundary.getScenarioTime()) : null;
    }
}
