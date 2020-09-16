/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import javax.inject.Inject;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.gridsuite.merge.orchestrator.server.dto.IgmQualityInfos;
import org.gridsuite.merge.orchestrator.server.dto.MergeInfos;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import com.powsybl.iidm.network.NetworkFactory;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = WebEnvironment.MOCK)
@ContextHierarchy({ @ContextConfiguration(classes = { MergeOrchestratorApplication.class,
        TestChannelBinderConfiguration.class }), })
public class MergeOrchestratorIT extends AbstractEmbeddedCassandraSetup {

    @Inject
    InputDestination input;

    @Inject
    OutputDestination output;

    @Inject
    MergeRepository mergeRepository;

    @Inject
    IgmQualityRepository igmQualityRepository;

    @Inject
    ParametersRepository parametersRepository;

    @MockBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private CaseFetcherService caseFetcherService;

    @MockBean
    private BalancesAdjustmentService balancesAdjustmentService;

    @MockBean
    private CopyToNetworkStoreService copyToNetworkStoreService;

    @MockBean
    private LoadFlowService loadFlowService;

    @Inject
    private MergeOrchestratorService mergeOrchestratorService;

    @Value("${parameters.run-balances-adjustment}")
    private boolean runBalancesAdjustment;

    private static final UUID UUID_CASE_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    private static final UUID UUID_CASE_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_NETWORK_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");

    private static final UUID UUID_CASE_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID UUID_NETWORK_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

    private static final UUID UUID_CASE_ID_UNKNOWN = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e9");

    @Test
    public void test() {
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 05, 01, 9, 00, 00, 00, ZoneId.of("UTC"));

        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_FR))
                .thenReturn(UUID_NETWORK_ID_FR);
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_ES))
                .thenReturn(UUID_NETWORK_ID_ES);
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_PT))
                .thenReturn(UUID_NETWORK_ID_PT);

        NetworkFactory networkFactory = NetworkFactory.find("Default");

        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_FR, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("fr", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_ES, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("es", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_PT, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("pt", "iidm"));

        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_FR))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_ES))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_PT))
                .thenReturn(true);

        // send first, expect single TSO_IGM message
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "1D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        Message<byte[]> messageFrIGM = output.receive(1000);
        assertEquals("TSO_IGM", messageFrIGM.getHeaders().get("type"));
        messageFrIGM = output.receive(1000);
        assertEquals("QUALITY_CHECK_NETWORK_STARTED", messageFrIGM.getHeaders().get("type"));
        messageFrIGM = output.receive(1000);
        assertEquals("QUALITY_CHECK_NETWORK_FINISHED", messageFrIGM.getHeaders().get("type"));
        List<MergeEntity> savedFr = mergeRepository.findAll();
        assertEquals(1, savedFr.size());
        assertEquals("QUALITY_CHECK_NETWORK_FINISHED", savedFr.get(0).getStatus());
        assertEquals(UUID_NETWORK_ID_FR, savedFr.get(0).getNetworkUuid());
        assertEquals("SWE", savedFr.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), savedFr.get(0).getKey().getDate());

        // send second, expect single TSO_IGM message
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(
                        List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "1D"),
                                new CaseInfos("es", UUID_CASE_ID_ES, "", "ES", "1D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        Message<byte[]> messageEsIGM = output.receive(1000);
        assertEquals("TSO_IGM", messageEsIGM.getHeaders().get("type"));
        messageEsIGM = output.receive(1000);
        assertEquals("QUALITY_CHECK_NETWORK_STARTED", messageEsIGM.getHeaders().get("type"));
        messageEsIGM = output.receive(1000);
        assertEquals("QUALITY_CHECK_NETWORK_FINISHED", messageEsIGM.getHeaders().get("type"));
        List<MergeEntity> savedEs = mergeRepository.findAll();
        assertEquals(1, savedEs.size());
        assertEquals("QUALITY_CHECK_NETWORK_FINISHED", savedEs.get(0).getStatus());
        assertEquals(UUID_NETWORK_ID_ES, savedEs.get(0).getNetworkUuid());
        assertEquals("SWE", savedEs.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), savedEs.get(0).getKey().getDate());

        // send out of scope tso, expect empty
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "XX")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_UNKNOWN.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        assertNull(output.receive(1000));

        // send third, expect TSO_IGM message, MERGE_STARTED and MERGE_FINISHED messages
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "1D"),
                        new CaseInfos("es", UUID_CASE_ID_ES, "", "ES", "1D"),
                        new CaseInfos("pt", UUID_CASE_ID_PT, "", "PT", "1D")));

        UUID mergedUuid = UUID.randomUUID();
        Mockito.when(copyToNetworkStoreService.copy(any())).thenReturn(mergedUuid);
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        Message<byte[]> messagePrIGM = output.receive(1000);
        assertEquals("TSO_IGM", messagePrIGM.getHeaders().get("type"));
        messagePrIGM = output.receive(1000);
        assertEquals("QUALITY_CHECK_NETWORK_STARTED", messagePrIGM.getHeaders().get("type"));
        messagePrIGM = output.receive(1000);
        assertEquals("QUALITY_CHECK_NETWORK_FINISHED", messagePrIGM.getHeaders().get("type"));

        Message<byte[]> messageMergeStarted = output.receive(1000);
        assertEquals("MERGE_PROCESS_STARTED", messageMergeStarted.getHeaders().get("type"));

        Message<byte[]> mergeNetworksStarted = output.receive(1000);
        assertEquals("MERGE_NETWORKS_STARTED", mergeNetworksStarted.getHeaders().get("type"));
        Message<byte[]> mergeNetworksFinished = output.receive(1000);
        assertEquals("MERGE_NETWORKS_FINISHED", mergeNetworksFinished.getHeaders().get("type"));
        Message<byte[]> mergedNetworksStored = output.receive(1000);
        assertEquals("MERGED_NETWORK_STORED", mergedNetworksStored.getHeaders().get("type"));

        if (runBalancesAdjustment) {
            Message<byte[]> balanceAdjustmentStarted = output.receive(1000);
            assertEquals("BALANCE_ADJUSTMENT_STARTED", balanceAdjustmentStarted.getHeaders().get("type"));

            Message<byte[]> balanceAdjustmentFinished = output.receive(1000);
            assertEquals("BALANCE_ADJUSTMENT_FINISHED", balanceAdjustmentFinished.getHeaders().get("type"));
        } else {
            Message<byte[]> loadFlowStarted = output.receive(1000);
            assertEquals("LOAD_FLOW_STARTED", loadFlowStarted.getHeaders().get("type"));

            Message<byte[]> loadFlowFinished = output.receive(1000);
            assertEquals("LOAD_FLOW_FINISHED", loadFlowFinished.getHeaders().get("type"));
        }

        Message<byte[]> mergeProcessFinished = output.receive(1000);
        assertEquals("MERGE_PROCESS_FINISHED", mergeProcessFinished.getHeaders().get("type"));

        List<MergeEntity> savedFinish = mergeRepository.findAll();
        assertEquals(1, savedFinish.size());
        assertEquals("MERGE_PROCESS_FINISHED", savedFinish.get(0).getStatus());
        assertEquals(mergedUuid, savedFinish.get(0).getNetworkUuid());
        assertEquals("SWE", savedFinish.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), savedFinish.get(0).getKey().getDate());

        List<MergeInfos> mergeInfos = mergeOrchestratorService.getMergesList();
        assertEquals(1, mergeInfos.size());
        assertEquals("SWE", mergeInfos.get(0).getProcess());
        assertEquals("MERGE_PROCESS_FINISHED", mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());

        mergeInfos = mergeOrchestratorService.getProcessMergesList("SWE");
        assertEquals(1, mergeInfos.size());
        assertEquals("SWE", mergeInfos.get(0).getProcess());
        assertEquals("MERGE_PROCESS_FINISHED", mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());

        Optional<MergeInfos> mergeInfo = mergeOrchestratorService.getMerge("SWE", ZonedDateTime.parse("2019-05-01T10:00:00.000+01:00"));
        assertTrue(mergeInfo.isPresent());
        assertEquals("SWE", mergeInfo.get().getProcess());
        assertEquals("MERGE_PROCESS_FINISHED", mergeInfo.get().getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfo.get().getDate().toLocalDateTime());

        Optional<IgmQualityInfos> qualityInfo = mergeOrchestratorService.getIgmQuality(UUID_CASE_ID_FR);
        assertTrue(qualityInfo.isPresent());
        assertEquals(UUID_CASE_ID_FR, qualityInfo.get().getCaseUuid());
        assertEquals(UUID_NETWORK_ID_FR, qualityInfo.get().getNetworkId());
        assertTrue(qualityInfo.get().isValid());

        qualityInfo = mergeOrchestratorService.getIgmQuality(UUID_CASE_ID_ES);
        assertTrue(qualityInfo.isPresent());
        assertEquals(UUID_CASE_ID_ES, qualityInfo.get().getCaseUuid());
        assertEquals(UUID_NETWORK_ID_ES, qualityInfo.get().getNetworkId());
        assertTrue(qualityInfo.get().isValid());

        qualityInfo = mergeOrchestratorService.getIgmQuality(UUID_CASE_ID_PT);
        assertTrue(qualityInfo.isPresent());
        assertEquals(UUID_CASE_ID_PT, qualityInfo.get().getCaseUuid());
        assertEquals(UUID_NETWORK_ID_PT, qualityInfo.get().getNetworkId());
        assertTrue(qualityInfo.get().isValid());

        qualityInfo = mergeOrchestratorService.getIgmQuality(UUID_CASE_ID_UNKNOWN);
        assertFalse(qualityInfo.isPresent());

        assertNull(output.receive(1000));
    }

    @Test
    public void parametersRepositoryTest() {
        assertEquals(0, parametersRepository.findAll().size());
        List<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        ParametersEntity parametersEntity = new ParametersEntity("SWE", tsos, false);
        parametersRepository.save(parametersEntity);
        assertEquals(1, parametersRepository.findAll().size());
        assertTrue(parametersRepository.findById("SWE").isPresent());
        assertEquals("SWE", parametersRepository.findById("SWE").get().getProcess());
        assertFalse(parametersRepository.findById("SWE").get().isRunBalancesAdjustment());
        assertEquals(3, parametersRepository.findById("SWE").get().getTsos().size());
    }
}
