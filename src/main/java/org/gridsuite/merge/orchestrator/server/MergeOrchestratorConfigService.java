/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelDeserializer;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfo;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorConstants.DELIMITER;
import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorConstants.REPORT_API_VERSION;
import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorException.Type.MERGE_REPORT_ERROR;
import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorException.Type.MERGE_REPORT_NOT_FOUND;

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

    private String reportServerBaseURI;

    private RestTemplate reportRestClient;

    @Autowired
    public MergeOrchestratorConfigService(@Value("${backing-services.report-server.base-uri:https://report-server}") String reportServerBaseURI,
                                          ProcessConfigRepository processConfigRepository,
                                          BoundaryRepository boundaryRepository,
                                          IgmRepository igmRepository,
                                          MergeRepository mergeRepository,
                                          NetworkStoreService networkStoreService) {
        this.processConfigRepository = processConfigRepository;
        this.boundaryRepository = boundaryRepository;
        this.mergeRepository = mergeRepository;
        this.igmRepository = igmRepository;
        this.networkStoreService = networkStoreService;
        setReportServerBaseURI(reportServerBaseURI);
    }

    RestTemplate getReportRestClient() {
        return reportRestClient;
    }

    void setReportServerBaseURI(String reportServerBaseURI) {
        this.reportServerBaseURI = reportServerBaseURI;
        this.reportRestClient = new RestTemplateBuilder().uriTemplateHandler(new DefaultUriBuilderFactory(getReportServerURI()))
                .messageConverters(getJackson2HttpMessageConverter())
                .build();
    }

    String getReportServerURI() {
        return this.reportServerBaseURI + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER;
    }

    private MappingJackson2HttpMessageConverter getJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ReporterModelJsonModule());
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReporterModelDeserializer.DICTIONARY_VALUE_ID, null));
        converter.setObjectMapper(objectMapper);
        return converter;
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

    public ReporterModel getReport(UUID report) {
        Objects.requireNonNull(report);
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/{reportId}");
            String uri = uriBuilder.build().toUriString();
            return reportRestClient.exchange(uri, HttpMethod.GET, null, ReporterModel.class, report.toString()).getBody();
        } catch (HttpClientErrorException e) {
            throw (e.getStatusCode() == HttpStatus.NOT_FOUND) ? new MergeOrchestratorException(MERGE_REPORT_NOT_FOUND, e) : new MergeOrchestratorException(MERGE_REPORT_ERROR, e);
        } catch (RestClientException e) {
            throw new MergeOrchestratorException(MERGE_REPORT_ERROR, e);
        }
    }

    public void deleteReport(UUID report) {
        Objects.requireNonNull(report);
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/{reportId}");
            String uri = uriBuilder.build().toUriString();
            reportRestClient.exchange(uri, HttpMethod.DELETE, null, ReporterModel.class, report.toString());
        } catch (HttpClientErrorException e) {
            throw (e.getStatusCode() == HttpStatus.NOT_FOUND) ? new MergeOrchestratorException(MERGE_REPORT_NOT_FOUND, e) : new MergeOrchestratorException(MERGE_REPORT_ERROR, e);
        } catch (RestClientException e) {
            throw new MergeOrchestratorException(MERGE_REPORT_ERROR, e);
        }
    }

    @Transactional
    public void deleteConfig(UUID processUuid) {
        igmRepository.findByKeyProcessUuid(processUuid).stream()
                .filter(Objects::nonNull)
                .map(IgmEntity::getNetworkUuid)
                .forEach(networkStoreService::deleteNetwork);
        igmRepository.deleteByKeyProcessUuid(processUuid);
        mergeRepository.getReportsFor(processUuid).stream().filter(Objects::nonNull).forEach(this::deleteReport);
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
