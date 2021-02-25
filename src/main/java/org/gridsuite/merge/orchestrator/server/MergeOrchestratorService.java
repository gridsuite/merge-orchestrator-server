/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.network.store.client.NetworkStoreService;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.merge.orchestrator.server.dto.*;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntity;
import org.gridsuite.merge.orchestrator.server.repositories.IgmRepository;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private static final String UNDERSCORE = "_";
    private static final String CGM = "CGM";

    private static final String GROOVY_TIMESTAMP_PARAMETER = "timestamp";
    private static final String GROOVY_PROCESS_NAME_PARAMETER = "processName";
    private static final String GROOVY_BUSINESS_PROCESS_PARAMETER = "businessProcess";

    private static final String ACCEPTED_FORMAT = "CGMES";

    private MergeRepository mergeRepository;

    private IgmRepository igmRepository;

    private CaseFetcherService caseFetcherService;

    private BalancesAdjustmentService balancesAdjustmentService;

    private LoadFlowService loadFlowService;

    private MergeEventService mergeEventService;

    private MergeOrchestratorConfigService mergeConfigService;

    private IgmQualityCheckService igmQualityCheckService;

    private NetworkConversionService networkConversionService;

    private NetworkStoreService networkStoreService;

    private Script replacingIGMScript;

    public MergeOrchestratorService(NetworkStoreService networkStoreService,
                                    CaseFetcherService caseFetchService,
                                    BalancesAdjustmentService balancesAdjustmentService,
                                    MergeEventService mergeEventService,
                                    LoadFlowService loadFlowService,
                                    IgmQualityCheckService igmQualityCheckService,
                                    MergeRepository mergeRepository,
                                    IgmRepository igmRepository,
                                    NetworkConversionService networkConversionService,
                                    MergeOrchestratorConfigService mergeConfigService) {
        this.networkStoreService = networkStoreService;
        this.caseFetcherService = caseFetchService;
        this.balancesAdjustmentService = balancesAdjustmentService;
        this.mergeEventService = mergeEventService;
        this.loadFlowService = loadFlowService;
        this.igmQualityCheckService = igmQualityCheckService;
        this.mergeRepository = mergeRepository;
        this.mergeConfigService = mergeConfigService;
        this.igmRepository = igmRepository;
        this.networkConversionService = networkConversionService;

        GroovyShell shell = new GroovyShell();
        try {
            replacingIGMScript = shell.parse(new InputStreamReader(new ClassPathResource("replaceIGM.groovy").getInputStream()));
        } catch (Exception exc) {
            LOGGER.error(exc.getMessage());
        }
    }

    @Bean
    public Consumer<Flux<Message<String>>> consumeNotification() {
        return f -> f.log(CATEGORY_BROKER_INPUT, Level.FINE).subscribe(this::consume);
    }

    private boolean isMatching(Tso ts, String tso) {
        return ts.getSourcingActor().equals(tso) || ts.getAlternativeSourcingActor().equals(tso);
    }

    private boolean isMatching(ProcessConfig config, String tso) {
        return config.getTsos().stream().anyMatch(ts -> isMatching(ts, tso));
    }

    private boolean checkTso(List<ProcessConfig> configs, String tso, String format, String businessProcess) {
        return StringUtils.equals(format, ACCEPTED_FORMAT) &&
                StringUtils.isNotEmpty(businessProcess) &&
                configs.stream().anyMatch(c -> isMatching(c, tso) && c.getBusinessProcess().equals(businessProcess));
    }

    public void consume(Message<String> message) {
        try {
            List<ProcessConfig> processConfigs = mergeConfigService.getConfigs();

            MessageHeaders mh = message.getHeaders();
            String date = (String) mh.get(DATE_HEADER_KEY);
            String tso = (String) mh.get(TSO_CODE_HEADER_KEY);
            UUID caseUuid = UUID.fromString((String) mh.get(UUID_HEADER_KEY));
            String format = (String) mh.get(FORMAT_HEADER_KEY);
            String businessProcess = (String) mh.get(BUSINESS_PROCESS_HEADER_KEY);

            if (checkTso(processConfigs, tso, format, businessProcess)) {
                // required tso received
                ZonedDateTime dateTime = ZonedDateTime.parse(date);

                for (ProcessConfig processConfig : processConfigs) {
                    if (processConfig.getTsos().stream().anyMatch(ts -> isMatching(ts, tso)) &&
                            processConfig.getBusinessProcess().equals(businessProcess)) {
                        LOGGER.info("Merge {} of process {} {} : IGM in format {} from TSO {} received", date, processConfig.getProcess(), processConfig.getBusinessProcess(), format, tso);
                        mergeEventService.addMergeIgmEvent(processConfig.getProcess(), processConfig.getBusinessProcess(), dateTime, tso, IgmStatus.AVAILABLE, null, null, null, null);
                    }
                }

                if (!processConfigs.isEmpty()) {
                    // import IGM into the network store
                    UUID networkUuid = caseFetcherService.importCase(caseUuid);
                    // check IGM quality
                    boolean valid = igmQualityCheckService.check(networkUuid);

                    merge(processConfigs.get(0), dateTime, date, tso, valid, networkUuid, caseUuid, businessProcess, null, null);

                    for (ProcessConfig processConfig : processConfigs.subList(1, processConfigs.size())) {
                        // import IGM into the network store
                        UUID processConfigNetworkUuid = caseFetcherService.importCase(caseUuid);
                        merge(processConfig, dateTime, date, tso, valid, processConfigNetworkUuid, caseUuid, businessProcess, null, null);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error : {}", e.getMessage());
        }
    }

    void merge(ProcessConfig processConfig, ZonedDateTime dateTime, String date, String tso,
               boolean valid, UUID networkUuid, UUID caseUuid, String businessProcess,
               ZonedDateTime replacingDate, String replacingBusinessProcess) {
        if (processConfig.getTsos().stream().anyMatch(ts -> isMatching(ts, tso)) &&
                processConfig.getBusinessProcess().equals(businessProcess)) {
            LOGGER.info("Merge {} of process {} {} : IGM from TSO {} is {}valid", date, processConfig.getProcess(), processConfig.getBusinessProcess(), tso, valid ? " " : "not ");
            mergeEventService.addMergeIgmEvent(processConfig.getProcess(), processConfig.getBusinessProcess(), dateTime, tso,
                    valid ? IgmStatus.VALIDATION_SUCCEED : IgmStatus.VALIDATION_FAILED, networkUuid, caseUuid,
                    replacingDate, replacingBusinessProcess);

            // get list of network UUID for validated IGMs
            List<IgmEntity> igmEntities = findfValidatedIgms(dateTime, processConfig.getProcess());
            List<UUID> networkUuids = igmEntities.stream().map(IgmEntity::getNetworkUuid).collect(Collectors.toList());
            if (networkUuids.size() == processConfig.getTsos().size()) {
                // all IGMs are available and valid for the merging process
                LOGGER.info("Merge {} of process {} {} : all IGMs have been received and are valid", date, processConfig.getProcess(), processConfig.getBusinessProcess());

                if (processConfig.isRunBalancesAdjustment()) {
                    // balances adjustment on the merge network
                    balancesAdjustmentService.doBalance(networkUuids);

                    LOGGER.info("Merge {} of process {} {} : balance adjustment complete", date, processConfig.getProcess(), processConfig.getBusinessProcess());

                    // TODO check balance adjustment status
                    mergeEventService.addMergeEvent(processConfig.getProcess(), processConfig.getBusinessProcess(), dateTime, MergeStatus.BALANCE_ADJUSTMENT_SUCCEED);
                } else {
                    // load flow on the merged network
                    loadFlowService.run(networkUuids);

                    LOGGER.info("Merge {} of process {} {} : loadflow complete", date, processConfig.getProcess(), processConfig.getBusinessProcess());

                    // TODO check loadflow status
                    mergeEventService.addMergeEvent(processConfig.getProcess(), processConfig.getBusinessProcess(), dateTime, MergeStatus.LOADFLOW_SUCCEED);
                }
            }
        }
    }

    private List<IgmEntity> findfValidatedIgms(ZonedDateTime dateTime, String process) {
        // Use of UTC Zone to store in cassandra database
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);

        return igmRepository.findByProcessAndDate(process, localDateTime).stream()
                .filter(mergeEntity -> mergeEntity.getStatus().equals(IgmStatus.VALIDATION_SUCCEED.name()))
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
                        ZonedDateTime date = ZonedDateTime.ofInstant(entity.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
                        if (merge.getDate().equals(date)) {
                            merge.getIgms().add(toIgm(entity));
                        }
                    }
                })
                .collect(Collectors.toList());
    }

    FileInfos exportMerge(String process, ZonedDateTime processDate, String format, String timeZoneOffset) throws IOException {
        List<IgmEntity> igmEntities =  findfValidatedIgms(processDate, process);
        List<UUID> networkUuids = igmEntities.stream().map(IgmEntity::getNetworkUuid).collect(Collectors.toList());
        List<UUID> caseUuid = igmEntities.stream().map(IgmEntity::getCaseUuid).collect(Collectors.toList());
        String businessProcess = mergeConfigService.getConfig(process).orElseThrow(() -> new PowsyblException("Business process " + process + "does not exist")).getBusinessProcess();
        LocalDateTime requesterDateTime = timeZoneOffset != null ? LocalDateTime.ofInstant(processDate.toInstant(), ZoneOffset.ofHours(Integer.parseInt(timeZoneOffset) / 60)) : processDate.toLocalDateTime();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMDD'T'HHmm'Z'");
        String baseFileName = requesterDateTime.format(formatter) + UNDERSCORE + businessProcess + UNDERSCORE + CGM + UNDERSCORE +  process;
        return networkConversionService.exportMerge(networkUuids, caseUuid, format, baseFileName);
    }

    private static Igm toIgm(IgmEntity entity) {
        ZonedDateTime replacingDate = entity.getReplacingDate() != null ? ZonedDateTime.ofInstant(entity.getReplacingDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")) : null;
        return new Igm(entity.getKey().getTso(), IgmStatus.valueOf(entity.getStatus()),
                replacingDate, entity.getReplacingBusinessProcess());
    }

    private static Merge toMerge(MergeEntity mergeEntity) {
        ZonedDateTime date = ZonedDateTime.ofInstant(mergeEntity.getKey().getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        return new Merge(mergeEntity.getKey().getProcess(), date, mergeEntity.getStatus() != null ? MergeStatus.valueOf(mergeEntity.getStatus()) : null, new ArrayList<>());
    }

    public Map<String, IgmReplacingInfo> replaceIGMs(String processName, ZonedDateTime processDate) {
        LocalDateTime ldt = LocalDateTime.ofInstant(processDate.toInstant(), ZoneOffset.UTC);

        // find missing or invalid igms
        List<String> missingOrInvalidTsos = new ArrayList<>();
        ProcessConfig config = mergeConfigService.getConfig(processName).orElse(null);
        if (config != null) {
            for (Tso tso : config.getTsos()) {
                Optional<IgmEntity> entity = igmRepository.findByProcessAndDateAndTso(processName, ldt, tso.getSourcingActor());
                if (!entity.isPresent() || !entity.get().getStatus().equals(IgmStatus.VALIDATION_SUCCEED.name())) {
                    missingOrInvalidTsos.add(tso.getSourcingActor());
                }
            }

            if (!missingOrInvalidTsos.isEmpty()) {
                return findReplacingIGM(config, processDate, missingOrInvalidTsos);
            }
        }

        return null;
    }

    public static List<ReplacingDate> execReplaceGroovyScript(Script replacingIGMScript, String date, String process, String businessProcess) {
        List<ReplacingDate> res = new ArrayList<>();

        Binding binding = new Binding();
        binding.setVariable(GROOVY_TIMESTAMP_PARAMETER, date);
        binding.setVariable(GROOVY_PROCESS_NAME_PARAMETER, process);
        binding.setVariable(GROOVY_BUSINESS_PROCESS_PARAMETER, businessProcess);

        replacingIGMScript.setBinding(binding);
        Object resScript = replacingIGMScript.run();

        for (Object elt : (List) resScript) {
            String value = (String) elt;
            String[] splitted = value.split("\\s+");
            res.add(new ReplacingDate(splitted[0], splitted[1]));
        }

        return res;
    }

    private Map<String, IgmReplacingInfo> findReplacingIGM(ProcessConfig config,
                                  ZonedDateTime processDate,
                                  List<String> missingOrInvalidTsos) {
        Map<String, IgmReplacingInfo> replacingIGMs = new HashMap<>();

        LocalDateTime localDateTime = LocalDateTime.ofInstant(processDate.toInstant(), ZoneOffset.UTC);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String formattedDate = processDate.format(formatter);

        // Execute groovy script to get the ordered proposed list of date, businessProcess for replacing the missing or invalid date, businessProcess
        List<ReplacingDate> resScript = execReplaceGroovyScript(replacingIGMScript, formattedDate, config.getProcess(), config.getBusinessProcess());

        // handle each missing or invalid igms
        for (String tso : missingOrInvalidTsos) {
            for (ReplacingDate elt : resScript) {
                ZonedDateTime replacingDate  = ZonedDateTime.parse(elt.getDate(), DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.of("UTC")));

                String replacingBusinessProcess = elt.getBusinessProcess();

                // search igm in the case server for the proposed replacing date, business process
                List<CaseInfos> casesinfo = caseFetcherService.getCases(Arrays.asList(tso), replacingDate, ACCEPTED_FORMAT, replacingBusinessProcess);
                if (!casesinfo.isEmpty()) {  // case found
                    UUID caseUuid = casesinfo.get(0).getUuid();

                    // no igm validation check is done here : it will be done later by the case validation server, as soon as a new case is
                    // imported in the case server
                    // so, we consider here that the replacing case is valid
                    LOGGER.info("Merge {} of process {} {} : IGM in format {} from TSO {} received", formattedDate,
                            config.getProcess(), config.getBusinessProcess(), ACCEPTED_FORMAT, tso);

                    Optional<IgmEntity> previousEntity = igmRepository.findByProcessAndDateAndTso(config.getProcess(), localDateTime, tso);
                    UUID currentNetworkUuid = previousEntity.isPresent() ? previousEntity.get().getNetworkUuid() : null;

                    mergeEventService.addMergeIgmEvent(config.getProcess(), config.getBusinessProcess(), processDate, tso, IgmStatus.AVAILABLE,
                            currentNetworkUuid, caseUuid, replacingDate, replacingBusinessProcess);

                    // import case in the network store
                    UUID networkUuid = caseFetcherService.importCase(caseUuid);

                    // info for the replacing igm : replacing date, replacing business process, status, networkUuid,
                    replacingIGMs.put(tso, new IgmReplacingInfo(tso, replacingDate, IgmStatus.VALIDATION_SUCCEED,
                            caseUuid, networkUuid, replacingBusinessProcess, currentNetworkUuid));

                    // A good candidate has been found for replacement
                    break;
                }
            }
        }

        // retriggering the merge computation
        for (Map.Entry<String, IgmReplacingInfo> igm : replacingIGMs.entrySet()) {
            String tso = igm.getKey();
            IgmReplacingInfo igmReplace = igm.getValue();

            LocalDateTime ldt = LocalDateTime.ofInstant(igmReplace.getDate().toInstant(), ZoneOffset.UTC);
            LocalDateTime processDt = LocalDateTime.ofInstant(processDate.toInstant(), ZoneOffset.UTC);

            // replace in merge_igm table : status, networkUuid for this igm at the initial date
            // with the status, networkUuid for this igm at the replacement date
            // and set also replacing date and replacing business process
            igmRepository.updateReplacingIgm(config.getProcess(), processDt, tso,
                    igmReplace.getStatus().name(), igmReplace.getNetworkUuid(), ldt,
                    igmReplace.getBusinessProcess());

            if (igmReplace.getOldNetworkUuid() != null) {
                // delete previous invalid imported network from network store
                networkStoreService.deleteNetwork(igmReplace.getOldNetworkUuid());
            }

            String formattedReplacingDate = igmReplace.getDate().format(formatter);

            LOGGER.info("Merge {} of process {} {} : IGM from TSO {} replaced by date {}", formattedDate,
                    config.getProcess(), config.getBusinessProcess(), tso, formattedReplacingDate);

            merge(config, processDate, formattedDate, tso, true, igmReplace.getNetworkUuid(), igmReplace.getCaseUuid(),
                    config.getBusinessProcess(), igmReplace.getDate(), igmReplace.getBusinessProcess());
        }

        return replacingIGMs;
    }
}
