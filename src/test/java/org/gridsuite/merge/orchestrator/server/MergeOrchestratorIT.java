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
import org.gridsuite.merge.orchestrator.server.repositories.*;
import com.powsybl.iidm.network.NetworkFactory;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.Merge;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntity;
import org.gridsuite.merge.orchestrator.server.repositories.IgmRepository;
import org.gridsuite.merge.orchestrator.server.repositories.MergeRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    IgmRepository igmRepository;

    @Inject
    ProcessConfigRepository processConfigRepository;

    @MockBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private CaseFetcherService caseFetcherService;

    @MockBean
    private BalancesAdjustmentService balancesAdjustmentService;

    @MockBean
    private LoadFlowService loadFlowService;

    @MockBean
    private NetworkConversionService networkConversionService;

    @Inject
    private MergeOrchestratorService mergeOrchestratorService;

    private boolean runBalancesAdjustment;

    private static final UUID UUID_CASE_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    private static final UUID UUID_CASE_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_NETWORK_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");

    private static final UUID UUID_CASE_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID UUID_NETWORK_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

    private static final UUID UUID_CASE_ID_UNKNOWN = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e9");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ArrayList<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        runBalancesAdjustment = false;
        processConfigRepository.save(new ProcessConfigEntity("SWE", tsos, false));
        processConfigRepository.save(new ProcessConfigEntity("FRES", tsos.subList(0, 2), false));
    }

    @Test
    public void test() {
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 0, 0, 0, ZoneId.of("UTC"));

        Mockito.when(loadFlowService.run(any()))
                .thenReturn(Mono.just("{\"status\": \"TRUE\"}"));

        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_FR))
                .thenReturn(Mono.just(UUID_NETWORK_ID_FR));
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_ES))
                .thenReturn(Mono.just(UUID_NETWORK_ID_ES));
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_PT))
                .thenReturn(Mono.just(UUID_NETWORK_ID_PT));

        NetworkFactory networkFactory = NetworkFactory.find("Default");

        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_FR, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("fr", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_ES, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("es", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_PT, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("pt", "iidm"));

        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_FR))
                .thenReturn(Mono.just(true));
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_ES))
                .thenReturn(Mono.just(true));
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_PT))
                .thenReturn(Mono.just(true));

        // send first
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "1D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        Message<byte[]> messageFrIGMProcess1 = output.receive(1000);
        assertEquals("AVAILABLE", messageFrIGMProcess1.getHeaders().get("status"));
        Message<byte[]> messageFrIGMProcess2 = output.receive(1000);
        assertEquals("AVAILABLE", messageFrIGMProcess2.getHeaders().get("status"));
        messageFrIGMProcess1 = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageFrIGMProcess1.getHeaders().get("status"));
        messageFrIGMProcess2 = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageFrIGMProcess2.getHeaders().get("status"));

        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        assertEquals(2, mergeEntities.size());
        assertEquals("SWE", mergeEntities.get(0).getKey().getProcess());
        assertEquals("FRES", mergeEntities.get(1).getKey().getProcess());

        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());
        List<IgmEntity> igmEntities = igmRepository.findAll();
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("SWE", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());

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
        assertEquals("AVAILABLE", messageEsIGM.getHeaders().get("status"));
        messageEsIGM = output.receive(1000);
        assertEquals("AVAILABLE", messageEsIGM.getHeaders().get("status"));
        messageEsIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageEsIGM.getHeaders().get("status"));
        messageEsIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageEsIGM.getHeaders().get("status"));
        messageEsIGM = output.receive(1000);
        assertEquals("LOADFLOW_SUCCEED", messageEsIGM.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        assertEquals(2, mergeEntities.size());
        igmEntities = igmRepository.findAll();
        assertEquals(4, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("SWE", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("SWE", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());

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

        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        Message<byte[]> messagePrIGM = output.receive(1000);
        assertEquals("AVAILABLE", messagePrIGM.getHeaders().get("status"));
        messagePrIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messagePrIGM.getHeaders().get("status"));

        Message<byte[]> messageMergeStarted = output.receive(1000);
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        assertEquals(2, mergeEntities.size());
        assertEquals("SWE", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        assertTrue(mergeOrchestratorService.getMerges("FOO").isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges("SWE");
        assertEquals(1, mergeInfos.size());
        assertEquals("SWE", mergeInfos.get(0).getProcess());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertFalse(mergeOrchestratorService.getMerges("SWE", dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));
    }

    @Test
    public void parametersRepositoryTest() {
        assertEquals(2, processConfigRepository.findAll().size());
        List<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        ProcessConfigEntity processConfigEntity = new ProcessConfigEntity("XYZ", tsos, true);
        processConfigRepository.save(processConfigEntity);
        assertEquals(3, processConfigRepository.findAll().size());
        assertTrue(processConfigRepository.findById("SWE").isPresent());
        assertTrue(processConfigRepository.findById("FRES").isPresent());
        assertTrue(processConfigRepository.findById("XYZ").isPresent());
        assertEquals("SWE", processConfigRepository.findById("SWE").get().getProcess());
        assertEquals("XYZ", processConfigRepository.findById("XYZ").get().getProcess());
        assertFalse(processConfigRepository.findById("SWE").get().isRunBalancesAdjustment());
        assertTrue(processConfigRepository.findById("XYZ").get().isRunBalancesAdjustment());
        assertEquals(3, processConfigRepository.findById("SWE").get().getTsos().size());
        assertEquals(2, processConfigRepository.findById("XYZ").get().getTsos().size());
    }

    @Test
    public void testParallel() throws InterruptedException {
        CountDownLatch never = new CountDownLatch(1);

        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_FR))
                .thenReturn(Mono.just(true));
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_PT))
                .thenReturn(Mono.just(false));

        Mockito.when(loadFlowService.run(any()))
                .thenReturn(Mono.just("{\"status\": \"TRUE\"}"));

        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_FR)).thenReturn(Mono.fromCallable(() -> {
            never.await();
            return UUID_CASE_ID_FR;
        }).subscribeOn(Schedulers.boundedElastic()));
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_PT)).thenReturn(Mono.just(UUID_NETWORK_ID_PT));

        List<Message<byte[]>> result = new ArrayList<>();
        CountDownLatch cdl = new CountDownLatch(1);
        // if we block in the reactor, all input.send and output.receive become blocking,
        // so we need to perform the whole test in another thread and kill it after a timeout
        (new Thread() {
            @Override
            public void run() {
                // send first, expected available only
                input.send(MessageBuilder.withPayload("")
                        .setHeader("tso", "FR")
                        .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                        .setHeader("uuid", UUID_CASE_ID_FR.toString())
                        .setHeader("format", "CGMES")
                        .setHeader("businessProcess", "1D")
                        .build());
                result.add(output.receive(1000)); // process 1
                result.add(output.receive(1000)); // process 2
                // send second, first shouldn't block second
                input.send(MessageBuilder.withPayload("")
                        .setHeader("tso", "PT")
                        .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                        .setHeader("uuid", UUID_CASE_ID_PT.toString())
                        .setHeader("format", "CGMES")
                        .setHeader("businessProcess", "1D")
                        .build());
                result.add(output.receive(1000)); // process 1
                result.add(output.receive(1000)); // invalid
                cdl.countDown();
            }
        }).start();
        cdl.await(5000, TimeUnit.MILLISECONDS);
        assertEquals(4, result.size());
        Message<byte[]> messageFr1IGM = result.get(0);
        Message<byte[]> messageFr2IGM = result.get(1);
        Message<byte[]> messagePtIGM = result.get(2);
        Message<byte[]> messagePtInvalidIGM = result.get(3);

        assertEquals("AVAILABLE", messageFr1IGM.getHeaders().get("status"));
        assertEquals("FR", messageFr1IGM.getHeaders().get("tso"));
        assertEquals("SWE", messageFr1IGM.getHeaders().get("process"));

        assertEquals("AVAILABLE", messageFr2IGM.getHeaders().get("status"));
        assertEquals("FR", messageFr2IGM.getHeaders().get("tso"));
        assertEquals("FRES", messageFr2IGM.getHeaders().get("process"));

        assertEquals("AVAILABLE", messagePtIGM.getHeaders().get("status"));
        assertEquals("PT", messagePtIGM.getHeaders().get("tso"));

        assertEquals("VALIDATION_FAILED", messagePtInvalidIGM.getHeaders().get("status"));
        assertEquals("PT", messagePtInvalidIGM.getHeaders().get("tso"));
    }
}
