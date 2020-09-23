/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.merge.orchestrator.server.dto.*;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntity;
import org.gridsuite.merge.orchestrator.server.repositories.IgmRepository;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class MergeOrchestratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorService.class);

    private static final String CATEGORY_BROKER_INPUT = MergeOrchestratorService.class.getName()
            + ".input-broker-messages";

    private static final String DATE_HEADER_KEY         = "date";
    private static final String TSO_CODE_HEADER_KEY     = "tso";
    private static final String UUID_HEADER_KEY         = "uuid";
    private static final String FORMAT_HEADER_KEY       = "format";
    private static final String BUSINESS_PROCESS_HEADER_KEY = "businessProcess";

    private static final String ACCEPTED_FORMAT = "CGMES";

    private MergeRepository mergeRepository;

    private IgmRepository igmRepository;

    private CaseFetcherService caseFetcherService;

    private BalancesAdjustmentService balancesAdjustmentService;

    private LoadFlowService loadFlowService;

    private MergeEventService mergeEventService;

    private MergeOrchestratorConfigService mergeConfigService;

    private IgmQualityCheckService igmQualityCheckService;

    private int poolSize;

    private int timeout;

    public MergeOrchestratorService(CaseFetcherService caseFetchService,
                                    BalancesAdjustmentService balancesAdjustmentService,
                                    MergeEventService mergeEventService,
                                    LoadFlowService loadFlowService,
                                    IgmQualityCheckService igmQualityCheckService,
                                    MergeRepository mergeRepository,
                                    IgmRepository igmRepository,
                                    MergeOrchestratorConfigService mergeConfigService,
                                    @Value("${threads.pool-size:8}") int poolSize,
                                    @Value("${threads.timeout:60}") int timeout) {
        this.caseFetcherService = caseFetchService;
        this.balancesAdjustmentService = balancesAdjustmentService;
        this.mergeEventService = mergeEventService;
        this.loadFlowService = loadFlowService;
        this.igmQualityCheckService = igmQualityCheckService;
        this.mergeRepository = mergeRepository;
        this.mergeConfigService = mergeConfigService;
        this.igmRepository = igmRepository;
        this.poolSize = poolSize;
        this.timeout = timeout;
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeNotification() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE).subscribe(this::consume);
    }

    private boolean checkTso(List<String> tsos, String tso, String format, String businessProcess) {
        return tsos.contains(tso) && StringUtils.equals(format, ACCEPTED_FORMAT) && StringUtils.isNotEmpty(businessProcess);
    }

    public void consume(Message<String> message) {
        try {
            List<ProcessConfig> processConfigs = mergeConfigService.getConfigs();

            List<String> tsos = mergeConfigService.getTsos();

            MessageHeaders mh = message.getHeaders();
            String date = (String) mh.get(DATE_HEADER_KEY);
            String tso = (String) mh.get(TSO_CODE_HEADER_KEY);
            UUID caseUuid = UUID.fromString((String) mh.get(UUID_HEADER_KEY));
            String format = (String) mh.get(FORMAT_HEADER_KEY);
            String businessProcess = (String) mh.get(BUSINESS_PROCESS_HEADER_KEY);

            if (checkTso(tsos, tso, format, businessProcess)) {
                //create the thread pool to parallelize merge processes and cas import
                ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize);

                // required tso received
                ZonedDateTime dateTime = ZonedDateTime.parse(date);

                for (ProcessConfig processConfig : processConfigs) {
                    if (processConfig.getTsos().contains(tso)) {
                        LOGGER.info("Merge {} of process {}: IGM in format {} from TSO {} received", date, processConfig.getProcess(), format, tso);
                        mergeEventService.addMergeIgmEvent(processConfig.getProcess(), dateTime, tso, IgmStatus.AVAILABLE, null);
                    }
                }

                if (!processConfigs.isEmpty()) {
                    Future<UUID> networkUuidFuture = executor.submit(() -> {
                        // import IGM into the network store
                        return caseFetcherService.importCase(caseUuid);
                    });

                    UUID networkUuid = networkUuidFuture.get(timeout, TimeUnit.SECONDS);

                    Future<Boolean> validFuture = executor.submit(() -> {
                        // check IGM quality
                        return igmQualityCheckService.check(networkUuid);
                    });

                    boolean valid = validFuture.get(timeout, TimeUnit.SECONDS);

                    executor.submit(() -> merge(processConfigs.get(0), dateTime, date, tso, valid, networkUuid));

                    for (ProcessConfig processConfig : processConfigs.subList(1, processConfigs.size())) {
                        executor.submit(() -> {
                            // import IGM into the network store
                            UUID processConfigNetworkUuid = caseFetcherService.importCase(caseUuid);
                            merge(processConfig, dateTime, date, tso, valid, processConfigNetworkUuid);
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error : {}", e.getMessage());
        }
    }

    void merge(ProcessConfig processConfig, ZonedDateTime dateTime, String date, String tso, boolean valid, UUID networkUuid) {
        if (processConfig.getTsos().contains(tso)) {
            LOGGER.info("Merge {} of process {}: IGM from TSO {} is {}valid", date, processConfig.getProcess(), tso, valid ? " " : "not ");
            mergeEventService.addMergeIgmEvent(processConfig.getProcess(), dateTime, tso,
                    valid ? IgmStatus.VALIDATION_SUCCEED : IgmStatus.VALIDATION_FAILED, networkUuid);

            // get list of network UUID for validated IGMs
            List<UUID> networkUuids = findNetworkUuidsOfValidatedIgms(dateTime, processConfig.getProcess());

            if (networkUuids.size() == processConfig.getTsos().size()) {
                // all IGMs are available and valid for the merging process
                LOGGER.info("Merge {} of process {}: all IGMs have been received and are valid", date, processConfig.getProcess());

                if (processConfig.isRunBalancesAdjustment()) {
                    // balances adjustment on the merge network
                    balancesAdjustmentService.doBalance(networkUuids);

                    LOGGER.info("Merge {} of process {}: balance adjustment complete", date, processConfig.getProcess());

                    // TODO check balance adjustment status
                    mergeEventService.addMergeEvent(processConfig.getProcess(), dateTime, MergeStatus.BALANCE_ADJUSTMENT_SUCCEED);
                } else {
                    // load flow on the merged network
                    loadFlowService.run(networkUuids);

                    LOGGER.info("Merge {} of process {}: loadflow complete", date, processConfig.getProcess());

                    // TODO check loadflow status
                    mergeEventService.addMergeEvent(processConfig.getProcess(), dateTime, MergeStatus.LOADFLOW_SUCCEED);
                }
            }
        }
    }

    private List<UUID> findNetworkUuidsOfValidatedIgms(ZonedDateTime dateTime, String process) {
        // Use of UTC Zone to store in cassandra database
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);

        return igmRepository.findByProcessAndDate(process, localDateTime).stream()
                .filter(mergeEntity -> mergeEntity.getStatus().equals(IgmStatus.VALIDATION_SUCCEED.name()))
                .map(IgmEntity::getNetworkUuid)
                .collect(Collectors.toList());
    }

    List<Merge> getMerges(String process) {
        Map<ZonedDateTime, Merge> mergesByDate = mergeRepository.findByProcess(process).stream()
                .map(MergeOrchestratorService::toMerge)
                .collect(Collectors.toMap(Merge::getDate, merge -> merge, (v1, v2) -> {
                    throw new IllegalStateException();
                }, TreeMap::new));
        for (IgmEntity entity : igmRepository.findByProcess(process)) {
            ZonedDateTime date = ZonedDateTime.ofInstant(entity.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
            mergesByDate.get(date).getIgms().add(toIgm(entity));
        }
        return new ArrayList<>(mergesByDate.values());
    }

    List<Merge> getMerges(String process, ZonedDateTime minDateTime, ZonedDateTime maxDateTime) {
        LocalDateTime minLocalDateTime = LocalDateTime.ofInstant(minDateTime.toInstant(), ZoneOffset.UTC);
        LocalDateTime maxLocalDateTime = LocalDateTime.ofInstant(maxDateTime.toInstant(), ZoneOffset.UTC);
        return mergeRepository.findByProcessAndInterval(process, minLocalDateTime, maxLocalDateTime)
                .stream()
                .map(MergeOrchestratorService::toMerge)
                .peek(merge -> {
                    for (IgmEntity entity : igmRepository.findByProcessAndInterval(process, minLocalDateTime, maxLocalDateTime)) {
                        merge.getIgms().add(toIgm(entity));
                    }
                })
                .collect(Collectors.toList());
    }

    private static Igm toIgm(IgmEntity entity) {
        return new Igm(entity.getKey().getTso(), IgmStatus.valueOf(entity.getStatus()));
    }

    private static Merge toMerge(MergeEntity mergeEntity) {
        ZonedDateTime date = ZonedDateTime.ofInstant(mergeEntity.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        return new Merge(mergeEntity.getKey().getProcess(), date, mergeEntity.getStatus() != null ? MergeStatus.valueOf(mergeEntity.getStatus()) : null, new ArrayList<>());
    }
}
