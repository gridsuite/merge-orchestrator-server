/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.iidm.network.NetworkFactory;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.merge.orchestrator.server.dto.*;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.gridsuite.merge.orchestrator.server.utils.MatcherIgm;
import org.gridsuite.merge.orchestrator.server.utils.MatcherIgmEntity;
import org.gridsuite.merge.orchestrator.server.utils.MatcherMerge;
import org.gridsuite.merge.orchestrator.server.utils.MatcherMergeEntity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = WebEnvironment.MOCK)
@ContextHierarchy({@ContextConfiguration(classes = {MergeOrchestratorApplication.class,
        TestChannelBinderConfiguration.class})})
public class MergeOrchestratorIT extends AbstractEmbeddedCassandraSetup {

    @Autowired
    InputDestination input;

    @Autowired
    OutputDestination output;

    @Autowired
    MergeRepository mergeRepository;

    @Autowired
    IgmRepository igmRepository;

    @Autowired
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

    @MockBean
    private CgmesBoundaryService cgmesBoundaryService;

    @Autowired
    private MergeOrchestratorService mergeOrchestratorService;

    @Autowired
    MergeOrchestratorConfigService mergeOrchestratorConfigService;

    @Value("${parameters.run-balances-adjustment}")
    private boolean runBalancesAdjustment;

    private static final UUID UUID_CASE_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    private static final UUID UUID_CASE_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_NETWORK_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");

    private static final UUID UUID_CASE_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID UUID_NETWORK_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID UUID_CASE_ID_PT_1 = UUID.fromString("d8babb72-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID UUID_NETWORK_ID_PT_1 = UUID.fromString("d8babb72-f60e-4766-bc5c-8f312c1984e4");

    private static final UUID FOO_1D_UUID = UUID.fromString("11111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID FRES_2D_UUID = UUID.fromString("21111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID FRPT_2D_UUID = UUID.fromString("31111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID SWE_1D_UUID = UUID.fromString("41111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID SWE_2D_UUID = UUID.fromString("51111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID XYZ_2D_UUID = UUID.fromString("61111111-f60e-4766-bc5c-8f312c1984e4");

    private static final UUID UUID_CASE_ID_UNKNOWN = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e9");

    private final NetworkFactory networkFactory = NetworkFactory.find("Default");
    private final ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 0, 0, 0, ZoneId.of("UTC"));

    private void createProcessConfigs() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_1D_UUID, "SWE_1D", "1D", List.of("FR", "ES", "PT"), false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_2D_UUID, "SWE_2D", "2D", List.of("FR", "ES", "PT"), false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false));
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(caseFetcherService.importCase(eq(UUID_CASE_ID_FR), any()))
                .thenReturn(UUID_NETWORK_ID_FR);
        Mockito.when(caseFetcherService.importCase(eq(UUID_CASE_ID_ES), any()))
                .thenReturn(UUID_NETWORK_ID_ES);
        Mockito.when(caseFetcherService.importCase(eq(UUID_CASE_ID_PT), any()))
                .thenReturn(UUID_NETWORK_ID_PT);
        Mockito.when(caseFetcherService.importCase(eq(UUID_CASE_ID_PT_1), any()))
                .thenReturn(UUID_NETWORK_ID_PT_1);

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
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_PT_1))
                .thenReturn(true);

        Mockito.when(loadFlowService.run(any()))
            .thenReturn(MergeStatus.FIRST_LOADFLOW_SUCCEED);
    }

    @Test
    public void testSingleMerge() {
        createProcessConfigs();

        // send first tso FR with business process = 1D, expect only one AVAILABLE and one VALIDATION_SUCCEED message
        Mockito.when(cgmesBoundaryService.getLastBoundaries())
            .thenReturn(List.of(new BoundaryInfos("f1582c44-d9e2-4ea0-afdc-dba189ab4358", "boundary1.xml", "fake content for boundary 1"),
                new BoundaryInfos("3e3f7738-aab9-4284-a965-71d5cd151f71", "boundary1.xml", "fake content for boundary 2")));

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
        assertEquals("AVAILABLE", messageFrIGM.getHeaders().get("status"));
        messageFrIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageFrIGM.getHeaders().get("status"));

        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_1D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        assertEquals(1, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());

        // send second tso ES with business process 1D, expect only one AVAILABLE and one VALIDATION_SUCCEED message
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
        assertEquals("VALIDATION_SUCCEED", messageEsIGM.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_1D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        igmEntities = igmRepository.findAll();
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());

        // send out of scope tso, expect no message
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "XX")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_UNKNOWN.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        assertNull(output.receive(1000));

        // send third tso PT with business process 1D, expect one AVAILABLE, one VALIDATION_SUCCEED
        // and one BALANCE_ADJUSTMENT_SUCCEED or FIRST_LOADFLOW_SUCCEED message (merge done)
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
        Message<byte[]> messagePtIGM = output.receive(1000);
        assertEquals("AVAILABLE", messagePtIGM.getHeaders().get("status"));
        messagePtIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messagePtIGM.getHeaders().get("status"));
        Message<byte[]> messageMergeStarted = output.receive(1000);
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_1D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        assertTrue(mergeOrchestratorService.getMerges(FOO_1D_UUID).isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges(SWE_1D_UUID);
        assertEquals(1, mergeInfos.size());
        assertEquals(SWE_1D_UUID, mergeInfos.get(0).getProcessUuid());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertFalse(mergeOrchestratorService.getMerges(SWE_1D_UUID, dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));
    }

    @Test
    public void testMultipleMerge() {
        createProcessConfigs();

        // send first tso FR with business process = 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process SWE_2D and FRES_2D)
        Mockito.when(cgmesBoundaryService.getLastBoundaries())
            .thenReturn(List.of(new BoundaryInfos("f1582c44-d9e2-4ea0-afdc-dba189ab4358", "boundary1.xml", "fake content for boundary 1"),
                new BoundaryInfos("3e3f7738-aab9-4284-a965-71d5cd151f71", "boundary1.xml", "fake content for boundary 2")));

        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "2D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
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
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertEquals(FRES_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, mergeEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(FRES_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());

        // send second tso ES with business process 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process SWE_2D and FRES_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or FIRST_LOADFLOW_SUCCEED message (merge done for process FRES_2D)
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(
                        List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "2D"),
                                new CaseInfos("es", UUID_CASE_ID_ES, "", "ES", "2D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());
        Message<byte[]> messageEsIGMProcess1 = output.receive(1000);
        assertEquals("AVAILABLE", messageEsIGMProcess1.getHeaders().get("status"));
        Message<byte[]> messageEsIGMProcess2 = output.receive(1000);
        assertEquals("AVAILABLE", messageEsIGMProcess2.getHeaders().get("status"));
        messageEsIGMProcess1 = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageEsIGMProcess1.getHeaders().get("status"));
        messageEsIGMProcess2 = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageEsIGMProcess2.getHeaders().get("status"));
        Message<byte[]> messageMergeStarted = output.receive(1000);
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertEquals(FRES_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, mergeEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(4, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(FRES_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(FRES_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(3).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(3).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(3).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(3).getKey().getDate());

        // send third tso PT with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process SWE_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or FIRST_LOADFLOW_SUCCEED message (merge done for process SWE_2D)
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "2D"),
                        new CaseInfos("es", UUID_CASE_ID_ES, "", "ES", "2D"),
                        new CaseInfos("pt", UUID_CASE_ID_PT, "", "PT", "2D")));

        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());
        Message<byte[]> messagePtIGM = output.receive(1000);
        assertEquals("AVAILABLE", messagePtIGM.getHeaders().get("status"));
        messagePtIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messagePtIGM.getHeaders().get("status"));
        messageMergeStarted = output.receive(1000);
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertEquals(FRES_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, mergeEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", mergeEntities.get(1).getStatus());

        assertTrue(mergeOrchestratorService.getMerges(FOO_1D_UUID).isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges(SWE_1D_UUID);
        assertEquals(0, mergeInfos.size());
        mergeInfos = mergeOrchestratorService.getMerges(SWE_2D_UUID);
        assertEquals(1, mergeInfos.size());
        assertEquals(SWE_2D_UUID, mergeInfos.get(0).getProcessUuid());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertTrue(mergeOrchestratorService.getMerges(SWE_1D_UUID, dateTime, dateTime).isEmpty());
        assertFalse(mergeOrchestratorService.getMerges(SWE_2D_UUID, dateTime, dateTime).isEmpty());
        assertFalse(mergeOrchestratorService.getMerges(FRES_2D_UUID, dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));

        // test delete config
        assertEquals(3, processConfigRepository.findAll().size());
        assertEquals("[MergeEntity(key=MergeEntityKey(processUuid=51111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00), status=FIRST_LOADFLOW_SUCCEED), MergeEntity(key=MergeEntityKey(processUuid=21111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00), status=FIRST_LOADFLOW_SUCCEED)]",
                mergeRepository.findAll().toString());
        assertEquals("[IgmEntity(key=IgmEntityKey(processUuid=51111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e5, caseUuid=7928181c-7977-4592-ba19-88027e4254e5, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71]), " +
                        "IgmEntity(key=IgmEntityKey(processUuid=51111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e4, caseUuid=7928181c-7977-4592-ba19-88027e4254e4, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71]), " +
                        "IgmEntity(key=IgmEntityKey(processUuid=51111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=PT), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e6, caseUuid=7928181c-7977-4592-ba19-88027e4254e6, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71]), " +
                        "IgmEntity(key=IgmEntityKey(processUuid=21111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e5, caseUuid=7928181c-7977-4592-ba19-88027e4254e5, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71]), " +
                        "IgmEntity(key=IgmEntityKey(processUuid=21111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e4, caseUuid=7928181c-7977-4592-ba19-88027e4254e4, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71])]",
            igmRepository.findAll().toString());

        mergeOrchestratorConfigService.deleteConfig(SWE_2D_UUID);

        assertEquals("[MergeEntity(key=MergeEntityKey(processUuid=21111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00), status=FIRST_LOADFLOW_SUCCEED)]",
                mergeRepository.findAll().toString());
        assertEquals("[IgmEntity(key=IgmEntityKey(processUuid=21111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e5, caseUuid=7928181c-7977-4592-ba19-88027e4254e5, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71]), " +
                        "IgmEntity(key=IgmEntityKey(processUuid=21111111-f60e-4766-bc5c-8f312c1984e4, date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e4, caseUuid=7928181c-7977-4592-ba19-88027e4254e4, replacingDate=null, replacingBusinessProcess=null, boundaries=[f1582c44-d9e2-4ea0-afdc-dba189ab4358, 3e3f7738-aab9-4284-a965-71d5cd151f71])]",
                igmRepository.findAll().toString());

        ArrayList<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_2D_UUID, "SWE_2D", "2D", tsos, false));
    }

    private void testImportIgmMessages(int nbOfTimes, boolean withMerge) {
        IntStream.range(0, nbOfTimes).forEach(i ->
                assertEquals("AVAILABLE", output.receive(1000).getHeaders().get("status"))
        );

        IntStream.range(0, nbOfTimes).forEach(i ->
                assertEquals("VALIDATION_SUCCEED", output.receive(1000).getHeaders().get("status"))
        );

        if (withMerge) {
            assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "FIRST_LOADFLOW_SUCCEED", output.receive(1000).getHeaders().get("status"));
        }
    }

    private void testMergeOk(Merge merge, List<String> tsos) {
        MergeStatus mergeStatusOk = runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED;
        assertThat(merge, new MatcherMerge(merge.getProcessUuid(), dateTime, mergeStatusOk));
        assertEquals(tsos.size(), merge.getIgms().size());
        IntStream.range(0, tsos.size()).forEach(i -> assertThat(merge.getIgms().get(i), new MatcherIgm(tsos.get(i), IgmStatus.VALIDATION_SUCCEED)));
    }

    @Test
    public void testDeleteMerge() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRPT_2D_UUID, "FRPT_2D", "2D", List.of("FR", "PT"), false));

        // send tsos FR, ES and PT with business process = 2D
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        testImportIgmMessages(2, false);
        testImportIgmMessages(1, true);
        testImportIgmMessages(1, true);

        testMergeOk(mergeOrchestratorService.getMerges(FRES_2D_UUID).get(0), List.of("ES", "FR"));
        testMergeOk(mergeOrchestratorService.getMerges(FRPT_2D_UUID).get(0), List.of("FR", "PT"));

        assertEquals(2, mergeRepository.findAll().size());
        assertEquals(4, igmRepository.findAll().size());

        mergeOrchestratorConfigService.deleteConfig(FRPT_2D_UUID);
        assertEquals(1, mergeRepository.findAll().size());
        assertEquals(2, igmRepository.findAll().size());

        mergeOrchestratorConfigService.deleteConfig(FRES_2D_UUID);
        assertEquals(0, mergeRepository.findAll().size());
        assertEquals(0, igmRepository.findAll().size());

        assertNull(output.receive(1000));
    }

    @Test
    public void testImportIgmByOnlyMatchingConfigs() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRPT_2D_UUID, "FRPT_2D", "2D", List.of("FR", "PT"), false));
        MergeStatus mergeStatusOk = runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED;

        // send first tso FR with business process = 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process FRES_2D and FRPT_2D)
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported twice
        testImportIgmMessages(2, false);

        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertThat(mergeEntities.get(0),
                new MatcherMergeEntity(FRES_2D_UUID, dateTime.toLocalDateTime(), null));
        assertThat(mergeEntities.get(1),
                new MatcherMergeEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), null));

        List<IgmEntity> igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(2, igmEntities.size());
        assertThat(igmEntities.get(0),
                new MatcherIgmEntity(FRES_2D_UUID, dateTime.toLocalDateTime(), "FR", IgmStatus.VALIDATION_SUCCEED, UUID_NETWORK_ID_FR));
        assertThat(igmEntities.get(1),
                new MatcherIgmEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), "FR", IgmStatus.VALIDATION_SUCCEED, UUID_NETWORK_ID_FR));

        // send second tso ES with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process FRES_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or FIRST_LOADFLOW_SUCCEED message (merge done for process FRES_2D)
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported once
        testImportIgmMessages(1, true);

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertThat(mergeEntities.get(0),
                new MatcherMergeEntity(FRES_2D_UUID, dateTime.toLocalDateTime(), mergeStatusOk));

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(3, igmEntities.size());
        assertThat(igmEntities.get(0),
                new MatcherIgmEntity(FRES_2D_UUID, dateTime.toLocalDateTime(), "ES", IgmStatus.VALIDATION_SUCCEED, UUID_NETWORK_ID_ES));

        // send third tso PT with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process FRPT_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or FIRST_LOADFLOW_SUCCEED message (merge done for process FRPT_2D)
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported once
        testImportIgmMessages(1, true);

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertThat(mergeEntities.get(1),
                new MatcherMergeEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), mergeStatusOk));

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(4, igmEntities.size());
        assertThat(igmEntities.get(3),
                new MatcherIgmEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), "PT", IgmStatus.VALIDATION_SUCCEED, UUID_NETWORK_ID_PT));

        List<Merge> mergeInfos = mergeOrchestratorService.getMerges(FRES_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("ES", "FR"));

        mergeInfos = mergeOrchestratorService.getMerges(FRPT_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("FR", "PT"));

        // send again tso PT with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process FRPT_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or FIRST_LOADFLOW_SUCCEED message (merge done for process FRPT_2D)
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT_1.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported once
        testImportIgmMessages(1, true);

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(4, igmEntities.size());
        assertThat(igmEntities.get(3),
                new MatcherIgmEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), "PT", IgmStatus.VALIDATION_SUCCEED, UUID_CASE_ID_PT_1));

        mergeInfos = mergeOrchestratorService.getMerges(FRES_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("ES", "FR"));

        mergeInfos = mergeOrchestratorService.getMerges(FRPT_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("FR", "PT"));

        assertNull(output.receive(1000));
    }

    @Test
    public void parametersRepositoryTest() {
        createProcessConfigs();
        assertEquals(3, processConfigRepository.findAll().size());
        List<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        ProcessConfigEntity processConfigEntity = new ProcessConfigEntity(XYZ_2D_UUID, "XYZ_2D", "2D", tsos, true);
        processConfigRepository.save(processConfigEntity);
        assertEquals(4, processConfigRepository.findAll().size());
        assertTrue(processConfigRepository.findById(SWE_1D_UUID).isPresent());
        assertTrue(processConfigRepository.findById(SWE_2D_UUID).isPresent());
        assertTrue(processConfigRepository.findById(FRES_2D_UUID).isPresent());
        assertTrue(processConfigRepository.findById(XYZ_2D_UUID).isPresent());
        assertEquals(SWE_1D_UUID, processConfigRepository.findById(SWE_1D_UUID).get().getProcessUuid());
        assertEquals("1D", processConfigRepository.findById(SWE_1D_UUID).get().getBusinessProcess());
        assertEquals(SWE_2D_UUID, processConfigRepository.findById(SWE_2D_UUID).get().getProcessUuid());
        assertEquals("2D", processConfigRepository.findById(SWE_2D_UUID).get().getBusinessProcess());
        assertEquals(FRES_2D_UUID, processConfigRepository.findById(FRES_2D_UUID).get().getProcessUuid());
        assertEquals("2D", processConfigRepository.findById(FRES_2D_UUID).get().getBusinessProcess());
        assertEquals(XYZ_2D_UUID, processConfigRepository.findById(XYZ_2D_UUID).get().getProcessUuid());
        assertEquals("2D", processConfigRepository.findById(XYZ_2D_UUID).get().getBusinessProcess());
        assertFalse(processConfigRepository.findById(SWE_1D_UUID).get().isRunBalancesAdjustment());
        assertFalse(processConfigRepository.findById(SWE_2D_UUID).get().isRunBalancesAdjustment());
        assertFalse(processConfigRepository.findById(FRES_2D_UUID).get().isRunBalancesAdjustment());
        assertTrue(processConfigRepository.findById(XYZ_2D_UUID).get().isRunBalancesAdjustment());
        assertEquals(3, processConfigRepository.findById(SWE_1D_UUID).get().getTsos().size());
        assertEquals(3, processConfigRepository.findById(SWE_2D_UUID).get().getTsos().size());
        assertEquals(2, processConfigRepository.findById(FRES_2D_UUID).get().getTsos().size());
        assertEquals(2, processConfigRepository.findById(XYZ_2D_UUID).get().getTsos().size());
    }

    @Test
    public void replacingIGMsTest() {
        // process dateTime : 2019-05_01T09:30:00Z
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 30, 0, 0, ZoneId.of("UTC"));

        mergeRepository.deleteAll();
        igmRepository.deleteAll();
        mergeOrchestratorConfigService.deleteConfig(SWE_2D_UUID);

        ArrayList<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_2D_UUID, "SWE_2D", "2D", tsos, false));

        // init incomplete merge and merge_igm data in database : missing ES and invalid PT igms
        mergeRepository.insert(new MergeEntity(new MergeEntityKey(SWE_2D_UUID, dateTime.toLocalDateTime()), null));
        igmRepository.insert(new IgmEntity(new IgmEntityKey(SWE_2D_UUID, dateTime.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_ID_FR, null, null, null, null));
        igmRepository.insert(new IgmEntity(new IgmEntityKey(SWE_2D_UUID, dateTime.toLocalDateTime(), "PT"), IgmStatus.VALIDATION_FAILED.name(), UUID_NETWORK_ID_PT, null, null, null, null));

        Mockito.when(cgmesBoundaryService.getLastBoundaries())
            .thenReturn(List.of(new BoundaryInfos("f1582c44-d9e2-4ea0-afdc-dba189ab4358", "boundary1.xml", "fake content for boundary 1"),
                new BoundaryInfos("3e3f7738-aab9-4284-a965-71d5cd151f71", "boundary1.xml", "fake content for boundary 2")));

        // 1 - test replacing ES igm (at dateTime : 2019-05_01T12:30:00Z)
        //
        UUID uuidReplacingCaseES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        ZonedDateTime replacingDateES = ZonedDateTime.of(2019, 5, 1, 12, 30, 0, 0, ZoneId.of("UTC"));

        Mockito.when(caseFetcherService.getCases(List.of("ES"), replacingDateES, "CGMES", "2D"))
                .thenReturn(List.of(new CaseInfos("20190501T1230Z_1D_REE_001.zip", uuidReplacingCaseES, "CGMES", "ES", "2D")));

        UUID uuidReplacingNetworkES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
        Mockito.when(caseFetcherService.importCase(eq(uuidReplacingCaseES), any()))
                .thenReturn(uuidReplacingNetworkES);

        Map<String, IgmReplacingInfo> resReplacing = mergeOrchestratorService.replaceIGMs(SWE_2D_UUID, dateTime);

        assertEquals(1, resReplacing.size());
        assertTrue(resReplacing.containsKey("ES"));
        assertEquals("ES", resReplacing.get("ES").getTso());
        assertEquals(replacingDateES, resReplacing.get("ES").getDate());
        assertEquals(uuidReplacingCaseES, resReplacing.get("ES").getCaseUuid());
        assertEquals(uuidReplacingNetworkES, resReplacing.get("ES").getNetworkUuid());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, resReplacing.get("ES").getStatus());
        assertEquals("2D", resReplacing.get("ES").getBusinessProcess());

        // test merge_igm replacement has been done for ES igm in cassandra
        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        assertEquals(3, igmEntities.size());
        assertEquals(uuidReplacingNetworkES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(replacingDateES.toLocalDateTime(), igmEntities.get(0).getReplacingDate());
        assertEquals("2D", igmEntities.get(0).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertNull(igmEntities.get(1).getReplacingDate());
        assertNull(igmEntities.get(1).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_PT, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_FAILED", igmEntities.get(2).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertNull(igmEntities.get(2).getReplacingDate());
        assertNull(igmEntities.get(2).getReplacingBusinessProcess());

        // test message has been sent for ES igm
        Message<byte[]> messageEsIGM = output.receive(1000);
        assertEquals("AVAILABLE", messageEsIGM.getHeaders().get("status"));
        assertEquals("ES", messageEsIGM.getHeaders().get("tso"));
        assertEquals(SWE_2D_UUID, messageEsIGM.getHeaders().get("processUuid"));
        messageEsIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageEsIGM.getHeaders().get("status"));
        assertEquals("ES", messageEsIGM.getHeaders().get("tso"));
        assertEquals(SWE_2D_UUID, messageEsIGM.getHeaders().get("processUuid"));

        // 2 - test replacing PT igm (at dateTime : 2019-05_01T17:30:00Z)
        UUID uuidReplacingCasePT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254f1");
        ZonedDateTime replacingDatePT = ZonedDateTime.of(2019, 5, 1, 17, 30, 0, 0, ZoneId.of("UTC"));

        Mockito.when(caseFetcherService.getCases(List.of("PT"), replacingDatePT, "CGMES", "2D"))
                .thenReturn(List.of(new CaseInfos("20190501T1730Z_1D_REN_001.zip", uuidReplacingCasePT, "CGMES", "PT", "2D")));

        UUID uuidReplacingNetworkPT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254f2");
        Mockito.when(caseFetcherService.importCase(eq(uuidReplacingCasePT), any()))
                .thenReturn(uuidReplacingNetworkPT);

        resReplacing = mergeOrchestratorService.replaceIGMs(SWE_2D_UUID, dateTime);

        assertEquals(1, resReplacing.size());
        assertTrue(resReplacing.containsKey("PT"));
        assertEquals("PT", resReplacing.get("PT").getTso());
        assertEquals(replacingDatePT, resReplacing.get("PT").getDate());
        assertEquals(uuidReplacingCasePT, resReplacing.get("PT").getCaseUuid());
        assertEquals(uuidReplacingNetworkPT, resReplacing.get("PT").getNetworkUuid());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, resReplacing.get("PT").getStatus());
        assertEquals("2D", resReplacing.get("PT").getBusinessProcess());

        // test merge_igm replacement has been done for PT igm in cassandra
        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals("FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        igmEntities = igmRepository.findAll();
        assertEquals(3, igmEntities.size());
        assertEquals(uuidReplacingNetworkES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(replacingDateES.toLocalDateTime(), igmEntities.get(0).getReplacingDate());
        assertEquals("2D", igmEntities.get(0).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertNull(igmEntities.get(1).getReplacingDate());
        assertNull(igmEntities.get(1).getReplacingBusinessProcess());

        assertEquals(uuidReplacingNetworkPT, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(replacingDatePT.toLocalDateTime(), igmEntities.get(2).getReplacingDate());
        assertEquals("2D", igmEntities.get(2).getReplacingBusinessProcess());

        // test message has been sent for PT igm
        Message<byte[]> messagePtIGM = output.receive(1000);
        assertEquals("AVAILABLE", messagePtIGM.getHeaders().get("status"));
        assertEquals("PT", messagePtIGM.getHeaders().get("tso"));
        assertEquals(SWE_2D_UUID, messagePtIGM.getHeaders().get("processUuid"));
        messagePtIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messagePtIGM.getHeaders().get("status"));
        assertEquals("PT", messagePtIGM.getHeaders().get("tso"));
        assertEquals(SWE_2D_UUID, messagePtIGM.getHeaders().get("processUuid"));

        // test message has been sent for merge load flow
        Message<byte[]> messageMerge = output.receive(1000);
        assertEquals("FIRST_LOADFLOW_SUCCEED", messageMerge.getHeaders().get("status"));
        assertEquals(SWE_2D_UUID, messageEsIGM.getHeaders().get("processUuid"));
    }
}
