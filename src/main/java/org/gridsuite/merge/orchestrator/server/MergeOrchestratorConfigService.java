/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfo;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorConstants.DELIMITER;
import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorConstants.REPORT_API_VERSION;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class MergeOrchestratorConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorConfigService.class);

    @Value("${backing-services.report-server.base-uri:https://report-server}")
    String reportServerURI;

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
    public void addConfig(ProcessConfig processConfig) {
        // saving boundaries if needed
        BoundaryEntity boundaryEqEntity = processConfig.getEqBoundary() != null ? boundaryRepository
            .findById(processConfig.getEqBoundary().getId())
            .orElseGet(() -> boundaryRepository.save(toBoundaryEntity(processConfig.getEqBoundary()))) : null;
        BoundaryEntity boundaryTpEntity = processConfig.getTpBoundary() != null ? boundaryRepository
            .findById(processConfig.getTpBoundary().getId())
            .orElseGet(() -> boundaryRepository.save(toBoundaryEntity(processConfig.getTpBoundary()))) : null;

        // saving config
        var entity = toProcessConfigEntity(processConfig, boundaryEqEntity, boundaryTpEntity);
        processConfigRepository.save(entity);
    }

    void deleteReport(UUID report) {
        try {
            var restTemplate = new RestTemplate();
            var resourceUrl = reportServerURI + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + report.toString();
            restTemplate.exchange(resourceUrl, HttpMethod.DELETE, null, ReporterModel.class);
        } catch (Exception exception) {
            LOGGER.error(exception.getMessage());
        }
    }

    @Transactional
    public void deleteConfig(UUID processUuid) {
        igmRepository.findByKeyProcessUuid(processUuid).stream()
                .filter(Objects::nonNull)
                .map(IgmEntity::getNetworkUuid)
                .forEach(networkStoreService::deleteNetwork);
        igmRepository.deleteByKeyProcessUuid(processUuid);
        mergeRepository.getReportsFor(processUuid).forEach(this::deleteReport);
        mergeRepository.deleteByKeyProcessUuid(processUuid);

        Optional<String> eqBoundary = processConfigRepository.findById(processUuid).map(entity -> entity.getEqBoundary() != null ? entity.getEqBoundary().getId() : null);
        Optional<String> tpBoundary = processConfigRepository.findById(processUuid).map(entity -> entity.getTpBoundary() != null ? entity.getTpBoundary().getId() : null);

        processConfigRepository.deleteById(processUuid);

        // delete the boundaries if they were the last one used by the deleted process config
        eqBoundary.ifPresent(boundary -> {
            if (processConfigRepository.findAll().stream().filter(entity -> entity.getEqBoundary() != null && entity.getEqBoundary().getId().equals(boundary)).count() == 0) {
                boundaryRepository.deleteById(boundary);
            }
        });
        tpBoundary.ifPresent(boundary -> {
            if (processConfigRepository.findAll().stream().filter(entity -> entity.getTpBoundary() != null && entity.getTpBoundary().getId().equals(boundary)).count() == 0) {
                boundaryRepository.deleteById(boundary);
            }
        });
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcessUuid(), processConfigEntity.getProcess(),
            processConfigEntity.getBusinessProcess(), processConfigEntity.getTsos(),
            processConfigEntity.isRunBalancesAdjustment(),
            processConfigEntity.isUseLastBoundarySet(),
            toBoundaryInfo(processConfigEntity.getEqBoundary()),
            toBoundaryInfo(processConfigEntity.getTpBoundary()));
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig, BoundaryEntity boundaryEq, BoundaryEntity boundaryTp) {
        boolean isNewProcessConfig = processConfig.getProcessUuid() == null;

        var entity = new ProcessConfigEntity(isNewProcessConfig ? UUID.randomUUID() : processConfig.getProcessUuid(),
            processConfig.getProcess(), processConfig.getBusinessProcess(), processConfig.getTsos(),
            processConfig.isRunBalancesAdjustment(),
            processConfig.isUseLastBoundarySet(),
            boundaryEq,
            boundaryTp);
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
