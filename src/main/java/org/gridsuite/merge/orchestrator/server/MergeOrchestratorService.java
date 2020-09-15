/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.gridsuite.merge.orchestrator.server.dto.IgmQualityInfos;
import org.gridsuite.merge.orchestrator.server.dto.MergeInfos;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private ParametersRepository parametersRepository;

    private MergeRepository mergeRepository;

    private IgmQualityRepository igmQualityRepository;

    private NetworkStoreService networkStoreService;

    private CaseFetcherService caseFetcherService;

    private BalancesAdjustmentService balancesAdjustmentService;

    private CopyToNetworkStoreService copyToNetworkStoreService;

    private LoadFlowService loadFlowService;

    private MergeEventService mergeEventService;

    private MergeOrchestratorConfigService mergeConfigService;

    private IgmQualityCheckService igmQualityCheckService;

    public MergeOrchestratorService(NetworkStoreService networkStoreService,
                                    CaseFetcherService caseFetchService,
                                    BalancesAdjustmentService balancesAdjustmentService,
                                    CopyToNetworkStoreService copyToNetworkStoreService,
                                    MergeEventService mergeEventService,
                                    LoadFlowService loadFlowService,
                                    IgmQualityCheckService igmQualityCheckService,
                                    MergeRepository mergeRepository,
                                    IgmQualityRepository igmQualityRepository,
                                    MergeOrchestratorConfigService mergeConfigService) {
        this.networkStoreService = networkStoreService;
        this.caseFetcherService = caseFetchService;
        this.balancesAdjustmentService = balancesAdjustmentService;
        this.copyToNetworkStoreService = copyToNetworkStoreService;
        this.mergeEventService = mergeEventService;
        this.loadFlowService = loadFlowService;
        this.igmQualityCheckService = igmQualityCheckService;
        this.mergeRepository = mergeRepository;
        this.mergeConfigService = mergeConfigService;
        this.igmQualityRepository = igmQualityRepository;
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
            List<String> tsos = mergeConfigService.getTsos();
            MessageHeaders mh = message.getHeaders();
            String date = (String) mh.get(DATE_HEADER_KEY);
            String tso = (String) mh.get(TSO_CODE_HEADER_KEY);
            UUID caseUuid = UUID.fromString((String) mh.get(UUID_HEADER_KEY));
            String format = (String) mh.get(FORMAT_HEADER_KEY);
            String businessProcess = (String) mh.get(BUSINESS_PROCESS_HEADER_KEY);

            LOGGER.info("**** MERGE ORCHESTRATOR : message received : date={} tso={} format={} businessProcess={} ****", date, tso, format, businessProcess);

            if (checkTso(tsos, tso, format, businessProcess)) {
                // required tso received
                ZonedDateTime dateTime = ZonedDateTime.parse(date);

                mergeEventService.addMergeEvent("", tso, "TSO_IGM", dateTime, null, mergeConfigService.getProcess());

                // import IGM into the network store
                UUID networkUuid = caseFetcherService.importCase(caseUuid);

                mergeEventService.addMergeEvent("", tso, "QUALITY_CHECK_NETWORK_STARTED", dateTime, networkUuid, mergeConfigService.getProcess());

                // check IGM quality
                boolean valid = igmQualityCheckService.check(networkUuid);

                // Use of UTC Zone to store in cassandra database
                igmQualityRepository.save(new IgmQualityEntity(caseUuid, networkUuid,
                        LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC), valid));

                mergeEventService.addMergeEvent("", tso, "QUALITY_CHECK_NETWORK_FINISHED", dateTime, networkUuid, mergeConfigService.getProcess());

                // get cases from the case server that matches list of tsos, date, forecastDistance and format
                List<CaseInfos> list = caseFetcherService.getCases(tsos, dateTime, format, businessProcess);

                if (allTsosAvailable(list, tsos)) {
                    // all tsos are available for the merging process
                    mergeEventService.addMergeEvent("", tsos.toString(), "MERGE_PROCESS_STARTED", dateTime, null, mergeConfigService.getProcess());

                    boolean allIGMsValid = true;

                    List<Network> listNetworks = new ArrayList<>();

                    // get all IGMs validity
                    for (CaseInfos info : list) {
                        Optional<IgmQualityInfos> quality = getIgmQuality(info.getUuid());
                        if (quality.isPresent()) {
                            if (quality.get().isValid()) {
                                LOGGER.info("**** MERGE ORCHESTRATOR : IGM quality of tso={} for date={} is OK ****", info.getTso(), date);
                                listNetworks.add(networkStoreService.getNetwork(quality.get().getNetworkId(), PreloadingStrategy.COLLECTION));
                            } else {
                                LOGGER.info("**** MERGE ORCHESTRATOR : IGM quality of tso={} for date={} is NOT OK ****", info.getTso(), date);
                                allIGMsValid = false;
                            }
                        } else {
                            LOGGER.info("**** MERGE ORCHESTRATOR : IGM quality of tso={} for date={} is UNDEFINED ****", info.getTso(), date);
                            allIGMsValid = false;
                        }
                    }

                    if (!allIGMsValid) {
                        return;
                    }

                    // all IGMs are available and valid for the merging process
                    LOGGER.info("**** MERGE ORCHESTRATOR : merging cases ******");

                    // merging view creation
                    MergingView mergingView = MergingView.create("merged", "iidm");

                    mergeEventService.addMergeEvent("", tsos.toString(), "MERGE_NETWORKS_STARTED", dateTime, null, mergeConfigService.getProcess());

                    // merge all IGM networks
                    mergingView.merge(listNetworks.toArray(new Network[listNetworks.size()]));

                    mergeEventService.addMergeEvent("", tsos.toString(), "MERGE_NETWORKS_FINISHED", dateTime, null, mergeConfigService.getProcess());

                    LOGGER.info("**** MERGE ORCHESTRATOR : copy to network store ******");

                    // store the merged network in the network store
                    UUID mergeUuid = copyToNetworkStoreService.copy(mergingView);

                    mergeEventService.addMergeEvent("", tsos.toString(), "MERGED_NETWORK_STORED", dateTime, mergeUuid, mergeConfigService.getProcess());

                    if (mergeConfigService.isRunBalancesAdjustment()) {
                        // balances adjustment on the merge network
                        LOGGER.info("**** MERGE ORCHESTRATOR : balances adjustment ******");
                        mergeEventService.addMergeEvent("", tsos.toString(), "BALANCE_ADJUSTMENT_STARTED", dateTime, mergeUuid, mergeConfigService.getProcess());
                        balancesAdjustmentService.doBalance(mergeUuid);
                        mergeEventService.addMergeEvent("", tsos.toString(), "BALANCE_ADJUSTMENT_FINISHED", dateTime, mergeUuid, mergeConfigService.getProcess());

                    } else {
                        // load flow on the merged network
                        LOGGER.info("**** MERGE ORCHESTRATOR : load flow ******");
                        mergeEventService.addMergeEvent("", tsos.toString(), "LOAD_FLOW_STARTED", dateTime, mergeUuid, mergeConfigService.getProcess());
                        loadFlowService.run(mergeUuid);
                        mergeEventService.addMergeEvent("", tsos.toString(), "LOAD_FLOW_FINISHED", dateTime, mergeUuid, mergeConfigService.getProcess());
                    }

                    mergeEventService.addMergeEvent("", tsos.toString(), "MERGE_PROCESS_FINISHED", dateTime, mergeUuid, mergeConfigService.getProcess());

                    LOGGER.info("**** MERGE ORCHESTRATOR : end ******");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error : {}", e.getMessage());
        }
    }

    private boolean allTsosAvailable(List<CaseInfos> list, List<String> tsos) {
        Set<String> setTsos = list.stream().map(CaseInfos::getTso).collect(Collectors.toSet());
        return setTsos.size() == tsos.size() &&
                tsos.stream().allMatch(setTsos::contains);
    }

    List<MergeInfos> getMergesList() {
        List<MergeEntity> mergeList = mergeRepository.findAll();
        return mergeList.stream().map(m -> new MergeInfos(m.getKey().getProcess(),
                ZonedDateTime.ofInstant(m.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")),
                m.getStatus())).collect(Collectors.toList());
    }

    List<MergeInfos> getProcessMergesList(String process) {
        List<MergeEntity> mergeList = mergeRepository.findByProcess(process);
        return mergeList.stream().map(m -> new MergeInfos(m.getKey().getProcess(),
                ZonedDateTime.ofInstant(m.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")),
                m.getStatus())).collect(Collectors.toList());
    }

    Optional<MergeInfos> getMerge(String process, ZonedDateTime dateTime) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
        Optional<MergeEntity> merge = mergeRepository.findById(new MergeEntityKey(process, localDateTime));
        return merge.map(this::toMergeInfo);
    }

    private MergeInfos toMergeInfo(MergeEntity mergeEntity) {
        return new MergeInfos(mergeEntity.getKey().getProcess(),
                ZonedDateTime.ofInstant(mergeEntity.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")),
                mergeEntity.getStatus());
    }

    Optional<IgmQualityInfos> getIgmQuality(UUID caseInfo) {
        Optional<IgmQualityEntity> quality = igmQualityRepository.findById(caseInfo);
        return quality.map(this::toQualityInfo);
    }

    private IgmQualityInfos toQualityInfo(IgmQualityEntity qualityEntity) {
        return new IgmQualityInfos(qualityEntity.getCaseUuid(), qualityEntity.getNetworkUuid(), qualityEntity.isValid());
    }

    List<ParametersEntity> getParameters() {
        return parametersRepository.findAll();
    }

    Optional<ParametersEntity> getParametersByProcess(String process) {
        return parametersRepository.findById(process);
    }

    void addParameters(ParametersEntity parametersEntity) {
        parametersRepository.save(parametersEntity);
    }
}
