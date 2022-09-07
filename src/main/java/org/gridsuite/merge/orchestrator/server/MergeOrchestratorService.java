/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.ReporterModel;
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
import org.gridsuite.merge.orchestrator.server.utils.CgmesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorException.Type.MERGE_NOT_FOUND;
import static org.gridsuite.merge.orchestrator.server.dto.ProcessConfig.ACCEPTED_FORMAT;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class MergeOrchestratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorService.class);

    private static final String CATEGORY_BROKER_INPUT = MergeOrchestratorService.class.getName()
            + ".input-broker-messages";

    private static final Logger LOGGER_BROKER_INPUT = LoggerFactory.getLogger(CATEGORY_BROKER_INPUT);

    private static final String DATE_HEADER_KEY = "date";
    private static final String TSO_CODE_HEADER_KEY = "tso";
    private static final String UUID_HEADER_KEY = "uuid";
    private static final String FORMAT_HEADER_KEY = "format";
    private static final String BUSINESS_PROCESS_HEADER_KEY = "businessProcess";
    private static final String UNDERSCORE = "_";
    private static final String CGM = "CGM";
    private static final String PROCESS = "Process";

    private static final String GROOVY_TIMESTAMP_PARAMETER = "timestamp";
    private static final String GROOVY_PROCESS_NAME_PARAMETER = "processName";
    private static final String GROOVY_BUSINESS_PROCESS_PARAMETER = "businessProcess";

    private MergeRepository mergeRepository;

    private IgmRepository igmRepository;

    private CaseFetcherService caseFetcherService;

    private BalancesAdjustmentService balancesAdjustmentService;

    private LoadFlowService loadFlowService;

    private MergeEventService mergeEventService;

    private MergeOrchestratorConfigService mergeConfigService;

    private IgmQualityCheckService igmQualityCheckService;

    private NetworkConversionService networkConversionService;

    private CgmesBoundaryService cgmesBoundaryService;

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
                                    MergeOrchestratorConfigService mergeConfigService,
                                    CgmesBoundaryService cgmesBoundaryService) {
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
        this.cgmesBoundaryService = cgmesBoundaryService;

        GroovyShell shell = new GroovyShell();
        try {
            replacingIGMScript = shell.parse(new InputStreamReader(new ClassPathResource("replaceIGM.groovy").getInputStream()));
        } catch (Exception exc) {
            LOGGER.error(exc.getMessage());
        }
    }

    @Bean
    public Consumer<Message<String>> consumeNotification() {
        return this::consume;
    }

    public void consume(Message<String> message) {
        LOGGER_BROKER_INPUT.debug("consume {}", message);
        try {
            MessageHeaders mh = message.getHeaders();
            String date = (String) mh.get(DATE_HEADER_KEY);
            String tso = (String) mh.get(TSO_CODE_HEADER_KEY);
            UUID caseUuid = UUID.fromString((String) Objects.requireNonNull(mh.get(UUID_HEADER_KEY)));
            String format = (String) mh.get(FORMAT_HEADER_KEY);
            String businessProcess = (String) mh.get(BUSINESS_PROCESS_HEADER_KEY);
            ZonedDateTime dateTime = ZonedDateTime.parse(Objects.requireNonNull(date));

            // Get all matching process configs
            List<ProcessConfig> matchingProcessConfigList = mergeConfigService.getConfigs().stream()
                    .filter(c -> c.isMatching(tso, format, businessProcess))
                    .collect(Collectors.toList());

            // Send all availability messages
            matchingProcessConfigList.forEach(processConfig -> {
                LOGGER.info("Merge {} of process {} {} : IGM in format {} from TSO {} received", date, processConfig.getProcess(), processConfig.getBusinessProcess(), format, tso);

                // if already received delete old network
                Optional<IgmEntity> igmEntityOptional = igmRepository.findByKeyProcessUuidAndKeyDateAndKeyTso(processConfig.getProcessUuid(), LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC), tso);
                igmEntityOptional.map(IgmEntity::getNetworkUuid).filter(Objects::nonNull).ifPresent(networkStoreService::deleteNetwork);

                mergeEventService.addMergeIgmEvent(processConfig.getProcessUuid(), processConfig.getBusinessProcess(), dateTime, tso, IgmStatus.AVAILABLE, null, null, null, null, null, null);
            });

            for (ProcessConfig processConfig : matchingProcessConfigList) {
                try {
                    // import IGM into the network store
                    List<BoundaryContent> configBoundaries = getProcessConfigBoundaries(processConfig);
                    if (configBoundaries.isEmpty()) {
                        var errorMessage = PROCESS + " " + processConfig.getProcess() + " (" + processConfig.getBusinessProcess() + ") : EQ and/or TP boundary not available !!";
                        mergeEventService.addErrorEvent(processConfig.getProcessUuid(), processConfig.getBusinessProcess(), errorMessage);
                        throw new PowsyblException(errorMessage);
                    }

                    String eqBoundary = getEqBoundary(configBoundaries);
                    String tpBoundary = getTpBoundary(configBoundaries);

                    LOGGER.info("Import case {} using boundaries ids EQ={}, TP={}", caseUuid, eqBoundary, tpBoundary);

                    var networkUuid = networkConversionService.importCase(caseUuid, configBoundaries);

                    // check IGM quality
                    // FIXME use merge id when check is 1 per merge
                    boolean valid = igmQualityCheckService.check(networkUuid, networkUuid);

                    merge(processConfig, dateTime, date, tso, valid, networkUuid, caseUuid, null, null, eqBoundary, tpBoundary);
                } catch (Exception e) {
                    mergeEventService.addMergeIgmEvent(processConfig.getProcessUuid(), processConfig.getBusinessProcess(), dateTime, tso, IgmStatus.VALIDATION_FAILED, null, null, null, null, null, null);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Merge error : ", e);
        }
    }

    public List<BoundaryContent> getProcessConfigBoundaries(ProcessConfig config) {
        if (config.isUseLastBoundarySet()) {
            return cgmesBoundaryService.getLastBoundaries();
        } else {
            Optional<BoundaryContent> eqBoundary = cgmesBoundaryService.getBoundary(config.getEqBoundary().getId());
            if (!eqBoundary.isPresent()) {
                LOGGER.error("EQ boundary with id {} not found !!", config.getEqBoundary().getId());
            }
            Optional<BoundaryContent> tpBoundary = cgmesBoundaryService.getBoundary(config.getTpBoundary().getId());
            if (!tpBoundary.isPresent()) {
                LOGGER.error("TP boundary with id {} not found !!", config.getTpBoundary().getId());
            }
            if (!eqBoundary.isPresent() || !tpBoundary.isPresent()) {
                return Collections.emptyList();
            }

            return List.of(new BoundaryContent(eqBoundary.get().getId(), eqBoundary.get().getFilename(), eqBoundary.get().getBoundary()),
                new BoundaryContent(tpBoundary.get().getId(), tpBoundary.get().getFilename(), tpBoundary.get().getBoundary()));
        }
    }

    private void merge(ProcessConfig processConfig, ZonedDateTime dateTime, String date, String tso,
                       boolean valid, UUID networkUuid, UUID caseUuid,
                       ZonedDateTime replacingDate, String replacingBusinessProcess,
                       String eqBoundary, String tpBoundary) {
        LOGGER.info("Merge {} of process {} {} : IGM from TSO {} is {}valid", date, processConfig.getProcess(), processConfig.getBusinessProcess(), tso, valid ? "" : "not ");

        mergeEventService.addMergeIgmEvent(processConfig.getProcessUuid(), processConfig.getBusinessProcess(), dateTime, tso,
                valid ? IgmStatus.VALIDATION_SUCCEED : IgmStatus.VALIDATION_FAILED, networkUuid, caseUuid,
                replacingDate, replacingBusinessProcess, eqBoundary, tpBoundary);

        // get list of network UUID for validated IGMs
        List<IgmEntity> igmEntities = findValidatedIgms(dateTime, processConfig.getProcessUuid());
        List<UUID> networkUuids = igmEntities.stream().map(IgmEntity::getNetworkUuid).collect(Collectors.toList());
        if (networkUuids.size() == processConfig.getTsos().size()) {
            // all IGMs are available and valid for the merging process
            LOGGER.info("Merge {} of process {} {} : all IGMs have been received and are valid", date, processConfig.getProcess(), processConfig.getBusinessProcess());

            checkUsedBoundaries(processConfig, igmEntities, cgmesBoundaryService, date);

            if (processConfig.isRunBalancesAdjustment()) {
                // balances adjustment on the merge network
                balancesAdjustmentService.doBalance(networkUuids);

                LOGGER.info("Merge {} of process {} {} : balance adjustment complete", date, processConfig.getProcess(), processConfig.getBusinessProcess());

                // TODO check balance adjustment status
                mergeEventService.addMergeEvent(processConfig.getProcessUuid(), processConfig.getBusinessProcess(), dateTime, MergeStatus.BALANCE_ADJUSTMENT_SUCCEED);
            } else {
                // load flow on the merged network
                var mergeEntity = mergeEventService.getOrCreateMergeEntity(processConfig.getProcessUuid(), dateTime);
                MergeStatus status = loadFlowService.run(networkUuids, mergeEntity.getReportUUID());

                LOGGER.info("Merge {} of process {} {} : loadflow complete with status {}", date, processConfig.getProcess(), processConfig.getBusinessProcess(), status);

                mergeEventService.addMergeEvent(processConfig.getProcessUuid(), processConfig.getBusinessProcess(), dateTime, status);
            }
        }
    }

    public List<IgmEntity> findIgmsByProcessUuidAndDate(UUID processUuid, LocalDateTime localDateTime) {
        return igmRepository.findByKeyProcessUuidAndKeyDate(processUuid, localDateTime);
    }

    public List<IgmEntity> findValidatedIgms(ZonedDateTime dateTime, UUID processUuid) {
        // Use of UTC Zone to store in database
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC);
        return findIgmsByProcessUuidAndDate(processUuid, localDateTime).stream()
                .filter(mergeEntity -> mergeEntity.getStatus().equals(IgmStatus.VALIDATION_SUCCEED.name()))
                .collect(Collectors.toList());
    }

    public List<IgmEntity> findAllIgms() {
        return igmRepository.findAll();
    }

    List<Merge> getMerges(UUID processUuid) {
        Map<ZonedDateTime, Merge> mergesByDate = new HashMap<>();
        List<MergeRepository.MergeIgm> mergeIgms = mergeRepository.findMergeWithIgmsByProcessUuid(processUuid);
        for (MergeRepository.MergeIgm mergeIgm : mergeIgms) {
            var date = ZonedDateTime.ofInstant(mergeIgm.getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
            mergesByDate.computeIfAbsent(date, k -> toMerge(mergeIgm));
            mergesByDate.get(date).getIgms().add(toIgm(mergeIgm));
        }
        return new ArrayList<>(mergesByDate.values());
    }

    List<Merge> getMerges(UUID processUuid, ZonedDateTime minDateTime, ZonedDateTime maxDateTime) {
        LocalDateTime minLocalDateTime = LocalDateTime.ofInstant(minDateTime.toInstant(), ZoneOffset.UTC);
        LocalDateTime maxLocalDateTime = LocalDateTime.ofInstant(maxDateTime.toInstant(), ZoneOffset.UTC);

        Map<ZonedDateTime, Merge> mergesByDate = new HashMap<>();
        List<MergeRepository.MergeIgm> mergeIgms = mergeRepository.findMergeWithIgmsByProcessUuidAndInterval(processUuid, minLocalDateTime, maxLocalDateTime);
        for (MergeRepository.MergeIgm mergeIgm : mergeIgms) {
            var date = ZonedDateTime.ofInstant(mergeIgm.getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
            mergesByDate.computeIfAbsent(date, k -> toMerge(mergeIgm));
            mergesByDate.get(date).getIgms().add(toIgm(mergeIgm));
        }
        return new ArrayList<>(mergesByDate.values());
    }

    FileInfos exportMerge(UUID processUuid, ZonedDateTime processDate, String format) {
        List<IgmEntity> igmEntities = findValidatedIgms(processDate, processUuid);
        List<UUID> networkUuids = igmEntities.stream().map(IgmEntity::getNetworkUuid).collect(Collectors.toList());
        List<UUID> caseUuid = igmEntities.stream().map(IgmEntity::getCaseUuid).collect(Collectors.toList());
        ProcessConfig processConfig = mergeConfigService.getConfig(processUuid).orElseThrow(() -> new PowsyblException(PROCESS + " " + processUuid + "does not exist"));
        List<BoundaryContent> boundaries = getProcessConfigBoundaries(processConfig);
        if (boundaries.isEmpty()) {
            throw new PowsyblException("Boundaries for process " + processUuid + " unavailable");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm'Z'");
        String baseFileName = processDate.toLocalDateTime().format(formatter) + UNDERSCORE + processConfig.getBusinessProcess() + UNDERSCORE + CGM + UNDERSCORE + processConfig.getProcess();
        return networkConversionService.exportMerge(networkUuids, caseUuid, format, baseFileName, boundaries);
    }

    ReporterModel getReport(UUID processUuid, LocalDateTime processDate) {
        MergeEntity mergeEntity = mergeRepository.findByKeyProcessUuidAndKeyDate(processUuid, processDate).orElseThrow(() -> new MergeOrchestratorException(MERGE_NOT_FOUND, "<" + processUuid + ", " + processDate + ">"));
        return mergeConfigService.getReport(mergeEntity.getReportUUID());
    }

    void deleteReport(UUID processUuid, LocalDateTime processDate) {
        MergeEntity mergeEntity = mergeRepository.findByKeyProcessUuidAndKeyDate(processUuid, processDate).orElseThrow(() -> new MergeOrchestratorException(MERGE_NOT_FOUND, "<" + processUuid + ", " + processDate + ">"));
        mergeConfigService.deleteReport(mergeEntity.getReportUUID());
    }

    private static Igm toIgm(MergeRepository.MergeIgm mergeIgm) {
        ZonedDateTime replacingDate = mergeIgm.getReplacingDate() != null ? ZonedDateTime.ofInstant(mergeIgm.getReplacingDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC")) : null;
        return new Igm(mergeIgm.getTso(), IgmStatus.valueOf(mergeIgm.getIgmStatus()),
                replacingDate, mergeIgm.getReplacingBusinessProcess());
    }

    private static Merge toMerge(MergeRepository.MergeIgm merge) {
        var date = ZonedDateTime.ofInstant(merge.getDate().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        return new Merge(merge.getProcessUuid(), date, merge.getStatus() != null ? MergeStatus.valueOf(merge.getStatus()) : null, new ArrayList<>());
    }

    public Map<String, IgmReplacingInfo> replaceIGMs(UUID processUuid, ZonedDateTime processDate) {
        LocalDateTime ldt = LocalDateTime.ofInstant(processDate.toInstant(), ZoneOffset.UTC);

        // find missing or invalid igms
        List<String> missingOrInvalidTsos = new ArrayList<>();
        ProcessConfig config = mergeConfigService.getConfig(processUuid).orElse(null);
        if (config != null) {
            for (String tso : config.getTsos()) {
                Optional<IgmEntity> entity = igmRepository.findByKeyProcessUuidAndKeyDateAndKeyTso(processUuid, ldt, tso);
                if (!entity.isPresent() || !entity.get().getStatus().equals(IgmStatus.VALIDATION_SUCCEED.name())) {
                    missingOrInvalidTsos.add(tso);
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

        List<BoundaryContent> configBoundaries = getProcessConfigBoundaries(config);
        if (configBoundaries.isEmpty()) {
            var errorMessage = PROCESS + " " + config.getProcess() + " (" + config.getBusinessProcess() + ") : EQ and/or TP boundary not available !!";
            mergeEventService.addErrorEvent(config.getProcessUuid(), config.getBusinessProcess(), errorMessage);
            return replacingIGMs;
        }

        // handle each missing or invalid igms
        for (String tso : missingOrInvalidTsos) {
            for (ReplacingDate elt : resScript) {
                ZonedDateTime replacingDate = ZonedDateTime.parse(elt.getDate(), DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.of("UTC")));

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

                    Optional<IgmEntity> previousEntity = igmRepository.findByKeyProcessUuidAndKeyDateAndKeyTso(config.getProcessUuid(), localDateTime, tso);
                    UUID currentNetworkUuid = previousEntity.isPresent() ? previousEntity.get().getNetworkUuid() : null;

                    try {
                        // import case in the network store
                        String eqBoundary = getEqBoundary(configBoundaries);
                        String tpBoundary = getTpBoundary(configBoundaries);

                        var networkUuid = networkConversionService.importCase(caseUuid, configBoundaries);

                        LOGGER.info("Import case {} using last boundaries ids EQ={}, TP={}", caseUuid, eqBoundary, tpBoundary);

                        mergeEventService.addMergeIgmEvent(config.getProcessUuid(), config.getBusinessProcess(), processDate, tso, IgmStatus.AVAILABLE,
                            currentNetworkUuid, caseUuid, replacingDate, replacingBusinessProcess, eqBoundary, tpBoundary);

                        // info for the replacing igm : replacing date, replacing business process, status, networkUuid,
                        replacingIGMs.put(tso, new IgmReplacingInfo(tso, replacingDate, IgmStatus.VALIDATION_SUCCEED,
                            caseUuid, networkUuid, replacingBusinessProcess, currentNetworkUuid, eqBoundary, tpBoundary));

                        // A good candidate has been found for replacement
                        break;
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage());
                    }
                }
            }
        }

        // retriggering the merge computation
        for (Map.Entry<String, IgmReplacingInfo> igm : replacingIGMs.entrySet()) {
            String tso = igm.getKey();
            IgmReplacingInfo igmReplace = igm.getValue();

            LocalDateTime ldt = LocalDateTime.ofInstant(igmReplace.getDate().toInstant(), ZoneOffset.UTC);
            LocalDateTime processDt = LocalDateTime.ofInstant(processDate.toInstant(), ZoneOffset.UTC);

            // replace old status, old networkUuid for this igm at the initial date
            // with new status, networkUuid for this igm at the replacement date
            // and set also replacing date, replacing business process and replacing boundaries
            updateReplacingIgm(config.getProcessUuid(), processDt, tso,
                    igmReplace.getStatus().name(), igmReplace.getNetworkUuid(), ldt,
                    igmReplace.getBusinessProcess(), igmReplace.getEqBoundary(), igmReplace.getTpBoundary());

            if (igmReplace.getOldNetworkUuid() != null) {
                // delete previous invalid imported network from network store
                networkStoreService.deleteNetwork(igmReplace.getOldNetworkUuid());
            }

            String formattedReplacingDate = igmReplace.getDate().format(formatter);

            LOGGER.info("Merge {} of process {} {} : IGM from TSO {} replaced by date {}", formattedDate,
                    config.getProcess(), config.getBusinessProcess(), tso, formattedReplacingDate);

            merge(config, processDate, formattedDate, tso, true, igmReplace.getNetworkUuid(), igmReplace.getCaseUuid(),
                    igmReplace.getDate(), igmReplace.getBusinessProcess(), igmReplace.getEqBoundary(), igmReplace.getTpBoundary());
        }

        return replacingIGMs;
    }

    @Transactional
    public void updateReplacingIgm(UUID processUuid, LocalDateTime date, String tso,
                                   String status, UUID networkUuid, LocalDateTime replacingDate, String replacingBusinessProcess,
                                   String replacingEqBoundary, String replacingTpBoundary) {
        Optional<IgmEntity> igmEntity = igmRepository.findByKeyProcessUuidAndKeyDateAndKeyTso(processUuid, date, tso);
        igmEntity.ifPresent(e -> {
            e.setStatus(status);
            e.setNetworkUuid(networkUuid);
            e.setReplacingDate(replacingDate);
            e.setReplacingBusinessProcess(replacingBusinessProcess);
            e.setEqBoundary(replacingEqBoundary);
            e.setTpBoundary(replacingTpBoundary);

            igmRepository.save(e);
        });
    }

    private static String getEqBoundary(List<BoundaryContent> boundaries) {
        return boundaries.stream().filter(b -> b.getFilename().matches(CgmesUtils.EQBD_FILE_REGEX)).findFirst().map(BoundaryContent::getId).orElse(null);
    }

    private static String getTpBoundary(List<BoundaryContent> boundaries) {
        return boundaries.stream().filter(b -> b.getFilename().matches(CgmesUtils.TPBD_FILE_REGEX)).findFirst().map(BoundaryContent::getId).orElse(null);
    }

    private void checkUsedBoundaries(ProcessConfig processConfig, List<IgmEntity> igmEntities, CgmesBoundaryService cgmesBoundaryService, String date) {
        // Add log if boundaries id used for each igm during import are different, or if they differ from last boundaries now available
        String eqBoundary = igmEntities.stream().findFirst().map(IgmEntity::getEqBoundary).orElse(null);
        String tpBoundary = igmEntities.stream().findFirst().map(IgmEntity::getTpBoundary).orElse(null);

        String finalEqBoundary = eqBoundary;
        String finalTpBoundary = tpBoundary;
        // check if boundaries id used for each igm during import are different
        if (!igmEntities.stream().skip(1).allMatch(e -> e.getEqBoundary() != null && finalEqBoundary != null && StringUtils.equals(e.getEqBoundary(), finalEqBoundary) && e.getTpBoundary() != null && finalTpBoundary != null && StringUtils.equals(e.getTpBoundary(), finalTpBoundary))) {
            LOGGER.warn("IGMs for merge process {} {} at {} have been imported with different boundaries !!!", processConfig.getProcess(), processConfig.getBusinessProcess(), date);
        } else {
            // check if boundaries id used for each igm differ from boundaries now available
            if (processConfig.isUseLastBoundarySet()) {
                List<BoundaryContent> lastBoundaries = cgmesBoundaryService.getLastBoundaries();
                eqBoundary = getEqBoundary(lastBoundaries);
                tpBoundary = getTpBoundary(lastBoundaries);
            } else {
                eqBoundary = cgmesBoundaryService.getBoundary(processConfig.getEqBoundary().getId()).map(BoundaryContent::getId).orElse(null);
                tpBoundary = cgmesBoundaryService.getBoundary(processConfig.getTpBoundary().getId()).map(BoundaryContent::getId).orElse(null);
            }

            String finalTpBoundary2 = tpBoundary;
            String finalEqBoundary2 = eqBoundary;
            if (!igmEntities.stream().allMatch(e -> e.getEqBoundary() != null && finalEqBoundary2 != null && StringUtils.equals(e.getEqBoundary(), finalEqBoundary2) && e.getTpBoundary() != null && finalTpBoundary2 != null && StringUtils.equals(e.getTpBoundary(), finalTpBoundary2))) {
                LOGGER.warn("IGMs have been imported with different boundaries than the current boundaries now available for merge process {} {} at {}", processConfig.getProcess(), processConfig.getBusinessProcess(), date);
            }
        }
    }
}
