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
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorException.Type.MERGE_CONFIG_ERROR;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Service
public class MergeOrchestratorConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorConfigService.class);

    private final ProcessConfigRepository processConfigRepository;

    private final MergeRepository mergeRepository;

    private final IgmRepository igmRepository;

    private final NetworkStoreService networkStoreService;

    private String reportServerBaseURI;

    private RestTemplate reportRestClient;

    @Autowired
    public MergeOrchestratorConfigService(@Value("${backing-services.report-server.base-uri:https://report-server}") String reportServerBaseURI,
                                          ProcessConfigRepository processConfigRepository,
                                          IgmRepository igmRepository,
                                          MergeRepository mergeRepository,
                                          NetworkStoreService networkStoreService) {
        this.processConfigRepository = processConfigRepository;
        this.mergeRepository = mergeRepository;
        this.igmRepository = igmRepository;
        this.networkStoreService = networkStoreService;
        setReportServerBaseURI(reportServerBaseURI);
    }

    public void setReportServerBaseURI(String reportServerBaseURI) {
        this.reportServerBaseURI = reportServerBaseURI;
        this.reportRestClient = new RestTemplateBuilder().uriTemplateHandler(new DefaultUriBuilderFactory(getReportServerURI()))
                .messageConverters(getJackson2HttpMessageConverter())
                .build();
    }

    public String getReportServerURI() {
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

    void addConfig(ProcessConfig processConfig) {
        processConfigRepository.save(toProcessConfigEntity(processConfig));
    }

    public ReporterModel getReport(UUID report) {
        Objects.requireNonNull(report);
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/{reportId}");
            String uri = uriBuilder.build().toUriString();
            return reportRestClient.exchange(uri, HttpMethod.GET, null, ReporterModel.class, report.toString()).getBody();
        } catch (Exception e) {
            throw new MergeOrchestratorException(MERGE_CONFIG_ERROR, e);
        }
    }

    public void deleteReport(UUID report) {
        Objects.requireNonNull(report);
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/{reportId}");
            String uri = uriBuilder.build().toUriString();
            reportRestClient.exchange(uri, HttpMethod.DELETE, null, ReporterModel.class, report.toString());
        } catch (Exception e) {
            throw new MergeOrchestratorException(MERGE_CONFIG_ERROR, e);
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
        processConfigRepository.deleteById(processUuid);
    }

    private ProcessConfig toProcessConfig(ProcessConfigEntity processConfigEntity) {
        return new ProcessConfig(processConfigEntity.getProcessUuid(), processConfigEntity.getProcess(), processConfigEntity.getBusinessProcess(), processConfigEntity.getTsos(), processConfigEntity.isRunBalancesAdjustment());
    }

    private ProcessConfigEntity toProcessConfigEntity(ProcessConfig processConfig) {
        boolean isNew = processConfig.getProcessUuid() == null;
        ProcessConfigEntity entity = new ProcessConfigEntity(isNew ? UUID.randomUUID() : processConfig.getProcessUuid(), processConfig.getProcess(), processConfig.getBusinessProcess(), processConfig.getTsos(), processConfig.isRunBalancesAdjustment());
        if (!isNew) {
            entity.markNotNew();
        }
        return entity;
    }
}
