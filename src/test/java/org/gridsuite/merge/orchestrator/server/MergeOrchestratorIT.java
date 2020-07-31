/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;

import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.gridsuite.merge.orchestrator.server.dto.MergeInfos;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
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

    @Test
    public void test() {
        ZonedDateTime dateTime = ZonedDateTime.parse("2019-05-01T10:00:00.000+01:00");

        // send first, expect single TSO_IGM message
        Mockito.when(caseFetcherService.getCases(any(), any()))
                .thenReturn(List.of(new CaseInfos("fr", UUID.randomUUID(), "")));
        input.send(MessageBuilder.withPayload("").setHeader("geographicalCode", "FR").setHeader("date", "2019-05-01T10:00:00.000+01:00").build());
        Message<byte[]> messageFrIGM = output.receive(1000);
        assertEquals("TSO_IGM", messageFrIGM.getHeaders().get("type"));
        List<MergeEntity> savedFr = mergeRepository.findAll();
        assertEquals(1, savedFr.size());
        assertEquals("TSO_IGM", savedFr.get(0).getStatus());
        assertNull(savedFr.get(0).getNetworkUuid());
        assertEquals("SWE", savedFr.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), savedFr.get(0).getKey().getDate());

        // send second, expect single TSO_IGM message
        Mockito.when(caseFetcherService.getCases(any(), any()))
                .thenReturn(
                        List.of(new CaseInfos("fr", UUID.randomUUID(), ""),
                                new CaseInfos("es", UUID.randomUUID(), "")));
        input.send(MessageBuilder.withPayload("").setHeader("geographicalCode", "ES").setHeader("date", "2019-05-01T10:00:00.000+01:00").build());
        Message<byte[]> messageEsIGM = output.receive(1000);
        assertEquals("TSO_IGM", messageEsIGM.getHeaders().get("type"));
        List<MergeEntity> savedEs = mergeRepository.findAll();
        assertEquals(1, savedEs.size());
        assertEquals("TSO_IGM", savedEs.get(0).getStatus());
        assertNull(savedEs.get(0).getNetworkUuid());
        assertEquals("SWE", savedEs.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), savedEs.get(0).getKey().getDate());

        // send out of scope tso, expect empty
        input.send(MessageBuilder.withPayload("").setHeader("geographicalCode", "XX").setHeader("date", "2019-05-01T10:00:00.000+01:00").build());
        assertNull(output.receive(1000));

        // send third, expect TSO_IGM message, MERGE_STARTED and MERGE_FINISHED messages
        Mockito.when(caseFetcherService.getCases(any(), any()))
                .thenReturn(List.of(
                        new CaseInfos("fr", UUID.randomUUID(), ""),
                        new CaseInfos("es", UUID.randomUUID(), ""),
                        new CaseInfos("pt", UUID.randomUUID(), "")));
        NetworkFactory networkFactory = NetworkFactory.find("Default");
        Mockito.when(caseFetcherService.getCase(any()))
                .thenReturn(networkFactory.createNetwork("fr", "iidm"))
                .thenReturn(networkFactory.createNetwork("es", "iidm"))
                .thenReturn(networkFactory.createNetwork("pt", "iidm"));
        UUID mergedUuid = UUID.randomUUID();
        Mockito.when(copyToNetworkStoreService.copy(any())).thenReturn(mergedUuid);
        input.send(MessageBuilder.withPayload("").setHeader("geographicalCode", "PT").setHeader("date", "2019-05-01T10:00:00.000+01:00").build());
        Message<byte[]> messagePrIGM = output.receive(1000);
        assertEquals("TSO_IGM", messagePrIGM.getHeaders().get("type"));
        Message<byte[]> messageMergeStarted = output.receive(1000);
        assertEquals("MERGE_PROCESS_STARTED", messageMergeStarted.getHeaders().get("type"));

        Message<byte[]> readNetworksStarted = output.receive(1000);
        assertEquals("READ_NETWORKS_STARTED", readNetworksStarted.getHeaders().get("type"));
        Message<byte[]> readNetworksFinished = output.receive(1000);
        assertEquals("READ_NETWORKS_FINISHED", readNetworksFinished.getHeaders().get("type"));

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

        Optional<MergeInfos> mergeInfo = mergeOrchestratorService.getMerge("SWE", "2019-05-01T10:00:00.000+01:00");
        assertTrue(mergeInfo.isPresent());
        assertEquals("SWE", mergeInfo.get().getProcess());
        assertEquals("MERGE_PROCESS_FINISHED", mergeInfo.get().getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfo.get().getDate().toLocalDateTime());

        assertNull(output.receive(1000));
    }
}
