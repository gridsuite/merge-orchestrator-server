/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.merge.orchestrator.server.dto.*;
import org.gridsuite.merge.orchestrator.server.repositories.*;
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

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

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

    @Inject
    MergeOrchestratorConfigService mergeOrchestratorConfigService;

    @Value("${parameters.run-balances-adjustment}")
    private boolean runBalancesAdjustment;

    private static final UUID UUID_CASE_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    private static final UUID UUID_CASE_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_NETWORK_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");

    private static final UUID UUID_CASE_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
    private static final UUID UUID_NETWORK_ID_PT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

    private static final UUID UUID_CASE_ID_UNKNOWN = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e9");

    private final NetworkFactory networkFactory = NetworkFactory.find("Default");
    private final ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 0, 0, 0, ZoneId.of("UTC"));

    private void createProcessConfigs() {
        Tso tsoFR = new Tso("FR", "");
        Tso tsoES = new Tso("ES", "");
        Tso tsoPT = new Tso("PT", "");
        List<Tso> allTsos = List.of(tsoFR, tsoES, tsoPT);
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("SWE_1D", "1D", allTsos, false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("SWE_2D", "2D", allTsos, false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("FRES_2D", "2D", List.of(tsoFR, tsoES), false));
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_FR))
                .thenReturn(UUID_NETWORK_ID_FR);
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_ES))
                .thenReturn(UUID_NETWORK_ID_ES);
        Mockito.when(caseFetcherService.importCase(UUID_CASE_ID_PT))
                .thenReturn(UUID_NETWORK_ID_PT);

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
    }

    @Test
    public void testSingleMerge() {
        createProcessConfigs();

        // send first tso FR with business process = 1D, expect only one AVAILABLE and one VALIDATION_SUCCEED message
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
        assertEquals("SWE_1D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        assertEquals(1, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("SWE_1D", igmEntities.get(0).getKey().getProcess());
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
        assertEquals("SWE_1D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        igmEntities = igmRepository.findAll();
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("SWE_1D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("SWE_1D", igmEntities.get(1).getKey().getProcess());
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
        // and one BALANCE_ADJUSTMENT_SUCCEED or LOADFLOW_SUCCEED message (merge done)
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
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals("SWE_1D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        assertTrue(mergeOrchestratorService.getMerges("FOO_1D").isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges("SWE_1D");
        assertEquals(1, mergeInfos.size());
        assertEquals("SWE_1D", mergeInfos.get(0).getProcess());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertFalse(mergeOrchestratorService.getMerges("SWE_1D", dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));
    }

    @Test
    public void testMultipleMerge() {
        createProcessConfigs();

        // send first tso FR with business process = 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process SWE_2D and FRES_2D)
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
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcess()));
        assertEquals(2, mergeEntities.size());
        assertEquals("FRES_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());
        assertEquals("SWE_2D", mergeEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcess()));
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("FRES_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("SWE_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());

        // send second tso ES with business process 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process SWE_2D and FRES_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or LOADFLOW_SUCCEED message (merge done for process FRES_2D)
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
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcess()));
        assertEquals(2, mergeEntities.size());
        assertEquals("FRES_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals("SWE_2D", mergeEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcess()));
        assertEquals(4, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("FRES_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("FRES_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals("SWE_2D", igmEntities.get(2).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(3).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(3).getStatus());
        assertEquals("SWE_2D", igmEntities.get(3).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(3).getKey().getDate());

        // send third tso PT with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process SWE_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or LOADFLOW_SUCCEED message (merge done for process SWE_2D)
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
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcess()));
        assertEquals(2, mergeEntities.size());
        assertEquals("FRES_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals("SWE_2D", mergeEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(1).getStatus());

        assertTrue(mergeOrchestratorService.getMerges("FOO_1D").isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges("SWE_1D");
        assertEquals(0, mergeInfos.size());
        mergeInfos = mergeOrchestratorService.getMerges("SWE_2D");
        assertEquals(1, mergeInfos.size());
        assertEquals("SWE_2D", mergeInfos.get(0).getProcess());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertTrue(mergeOrchestratorService.getMerges("SWE_1D", dateTime, dateTime).isEmpty());
        assertFalse(mergeOrchestratorService.getMerges("SWE_2D", dateTime, dateTime).isEmpty());
        assertFalse(mergeOrchestratorService.getMerges("FRES_2D", dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));

        // test delete config
        assertEquals(3, processConfigRepository.findAll().size());
        assertEquals("[MergeEntity(key=MergeEntityKey(process=SWE_2D, date=2019-05-01T09:00), status=LOADFLOW_SUCCEED), MergeEntity(key=MergeEntityKey(process=FRES_2D, date=2019-05-01T09:00), status=LOADFLOW_SUCCEED)]",
                mergeRepository.findAll().toString());
        assertEquals("[IgmEntity(key=IgmEntityKey(process=SWE_2D, date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e5, caseUuid=7928181c-7977-4592-ba19-88027e4254e5, replacingDate=null, replacingBusinessProcess=null), " +
                        "IgmEntity(key=IgmEntityKey(process=SWE_2D, date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e4, caseUuid=7928181c-7977-4592-ba19-88027e4254e4, replacingDate=null, replacingBusinessProcess=null), " +
                        "IgmEntity(key=IgmEntityKey(process=SWE_2D, date=2019-05-01T09:00, tso=PT), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e6, caseUuid=7928181c-7977-4592-ba19-88027e4254e6, replacingDate=null, replacingBusinessProcess=null), " +
                        "IgmEntity(key=IgmEntityKey(process=FRES_2D, date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e5, caseUuid=7928181c-7977-4592-ba19-88027e4254e5, replacingDate=null, replacingBusinessProcess=null), " +
                        "IgmEntity(key=IgmEntityKey(process=FRES_2D, date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e4, caseUuid=7928181c-7977-4592-ba19-88027e4254e4, replacingDate=null, replacingBusinessProcess=null)]",
                igmRepository.findAll().toString());

        mergeOrchestratorConfigService.deleteConfig("SWE_2D");

        assertEquals("[MergeEntity(key=MergeEntityKey(process=FRES_2D, date=2019-05-01T09:00), status=LOADFLOW_SUCCEED)]",
                mergeRepository.findAll().toString());
        assertEquals("[IgmEntity(key=IgmEntityKey(process=FRES_2D, date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e5, caseUuid=7928181c-7977-4592-ba19-88027e4254e5, replacingDate=null, replacingBusinessProcess=null), " +
                        "IgmEntity(key=IgmEntityKey(process=FRES_2D, date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=7928181c-7977-4592-ba19-88027e4254e4, caseUuid=7928181c-7977-4592-ba19-88027e4254e4, replacingDate=null, replacingBusinessProcess=null)]",
                igmRepository.findAll().toString());

        ArrayList<Tso> tsos = new ArrayList<>();
        tsos.add(new Tso("FR", ""));
        tsos.add(new Tso("ES", ""));
        tsos.add(new Tso("PT", ""));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("SWE_2D", "2D", tsos, false));
    }

    @Test
    public void testImportIgmByOnlyConfigsConcerned() {
        Tso tsoFR = new Tso("FR", "");
        Tso tsoES = new Tso("ES", "");
        Tso tsoPT = new Tso("PT", "");
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("FRES_2D", "2D", List.of(tsoFR, tsoES), false));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("FRPT_2D", "2D", List.of(tsoFR, tsoPT), false));

        // send first tso FR with business process = 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process FRES_2D and FRPT_2D)
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "2D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported twice
        Message<byte[]> message = output.receive(1000);
        assertEquals("AVAILABLE", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals("AVAILABLE", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", message.getHeaders().get("status"));

        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcess()));
        assertEquals(2, mergeEntities.size());
        assertEquals("FRES_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());
        assertEquals("FRPT_2D", mergeEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcess()));
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("FRES_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("FRPT_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());

        // send second tso ES with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process FRES_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or LOADFLOW_SUCCEED message (merge done for process FRES_2D)
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(
                        List.of(new CaseInfos("es", UUID_CASE_ID_ES, "", "ES", "2D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported once
        message = output.receive(1000);
        assertEquals("AVAILABLE", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", message.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcess()));
        assertEquals(2, mergeEntities.size());
        assertEquals("FRES_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals("FRPT_2D", mergeEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcess()));
        assertEquals(3, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("FRES_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("FRES_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals("FRPT_2D", igmEntities.get(2).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());

        // send third tso PT with business process 2D, expect one AVAILABLE and one VALIDATION_SUCCEED message
        // (for process FRPT_2D),
        // and expect BALANCE_ADJUSTMENT_SUCCEED or LOADFLOW_SUCCEED message (merge done for process FRPT_2D)
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(new CaseInfos("pt", UUID_CASE_ID_PT, "", "PT", "2D")));

        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // Imported once
        message = output.receive(1000);
        assertEquals("AVAILABLE", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", message.getHeaders().get("status"));
        message = output.receive(1000);
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", message.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcess()));
        assertEquals(2, mergeEntities.size());
        assertEquals("FRES_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals("FRPT_2D", mergeEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertEquals(runBalancesAdjustment ? "BALANCE_ADJUSTMENT_SUCCEED" : "LOADFLOW_SUCCEED", mergeEntities.get(1).getStatus());

        igmEntities = igmRepository.findAll();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcess()));
        assertEquals(4, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("FRES_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("FRES_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals("FRPT_2D", igmEntities.get(2).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_PT, igmEntities.get(3).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(3).getStatus());
        assertEquals("FRPT_2D", igmEntities.get(3).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(3).getKey().getDate());

        List<Merge> mergeInfos = mergeOrchestratorService.getMerges("FRES_2D");
        assertEquals(1, mergeInfos.size());
        assertEquals("FRES_2D", mergeInfos.get(0).getProcess());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(2, mergeInfos.get(0).getIgms().size());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());

        mergeInfos = mergeOrchestratorService.getMerges("FRPT_2D");
        assertEquals(1, mergeInfos.size());
        assertEquals("FRPT_2D", mergeInfos.get(0).getProcess());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(2, mergeInfos.get(0).getIgms().size());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());

        assertNull(output.receive(1000));
    }

    @Test
    public void parametersRepositoryTest() {
        createProcessConfigs();
        assertEquals(3, processConfigRepository.findAll().size());
        List<TsoEntity> tsos = new ArrayList<>();
        tsos.add(new TsoEntity("FR", ""));
        tsos.add(new TsoEntity("ES", ""));
        ProcessConfigEntity processConfigEntity = new ProcessConfigEntity("XYZ_2D", "2D", tsos, true);
        processConfigRepository.save(processConfigEntity);
        assertEquals(4, processConfigRepository.findAll().size());
        assertTrue(processConfigRepository.findById("SWE_1D").isPresent());
        assertTrue(processConfigRepository.findById("SWE_2D").isPresent());
        assertTrue(processConfigRepository.findById("FRES_2D").isPresent());
        assertTrue(processConfigRepository.findById("XYZ_2D").isPresent());
        assertEquals("SWE_1D", processConfigRepository.findById("SWE_1D").get().getProcess());
        assertEquals("1D", processConfigRepository.findById("SWE_1D").get().getBusinessProcess());
        assertEquals("SWE_2D", processConfigRepository.findById("SWE_2D").get().getProcess());
        assertEquals("2D", processConfigRepository.findById("SWE_2D").get().getBusinessProcess());
        assertEquals("FRES_2D", processConfigRepository.findById("FRES_2D").get().getProcess());
        assertEquals("2D", processConfigRepository.findById("FRES_2D").get().getBusinessProcess());
        assertEquals("XYZ_2D", processConfigRepository.findById("XYZ_2D").get().getProcess());
        assertEquals("2D", processConfigRepository.findById("XYZ_2D").get().getBusinessProcess());
        assertFalse(processConfigRepository.findById("SWE_1D").get().isRunBalancesAdjustment());
        assertFalse(processConfigRepository.findById("SWE_2D").get().isRunBalancesAdjustment());
        assertFalse(processConfigRepository.findById("FRES_2D").get().isRunBalancesAdjustment());
        assertTrue(processConfigRepository.findById("XYZ_2D").get().isRunBalancesAdjustment());
        assertEquals(3, processConfigRepository.findById("SWE_1D").get().getTsos().size());
        assertEquals(3, processConfigRepository.findById("SWE_2D").get().getTsos().size());
        assertEquals(2, processConfigRepository.findById("FRES_2D").get().getTsos().size());
        assertEquals(2, processConfigRepository.findById("XYZ_2D").get().getTsos().size());
    }

    @Test
    public void replacingIGMsTest() {
        // process dateTime : 2019-05_01T09:30:00Z
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 30, 0, 0, ZoneId.of("UTC"));

        mergeRepository.deleteAll();
        igmRepository.deleteAll();
        mergeOrchestratorConfigService.deleteConfig("SWE_2D");

        ArrayList<Tso> tsos = new ArrayList<>();
        tsos.add(new Tso("FR", ""));
        tsos.add(new Tso("ES", ""));
        tsos.add(new Tso("PT", ""));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig("SWE_2D", "2D", tsos, false));

        // init incomplete merge and merge_igm data in database : missing ES and invalid PT igms
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("SWE_2D", dateTime.toLocalDateTime()), null));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_2D", dateTime.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_ID_FR, null, null, null));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_2D", dateTime.toLocalDateTime(), "PT"), IgmStatus.VALIDATION_FAILED.name(), UUID_NETWORK_ID_PT, null, null, null));

        // 1 - test replacing ES igm (at dateTime : 2019-05_01T12:30:00Z)
        //
        UUID uuidReplacingCaseES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        ZonedDateTime replacingDateES = ZonedDateTime.of(2019, 5, 1, 12, 30, 0, 0, ZoneId.of("UTC"));

        Mockito.when(caseFetcherService.getCases(List.of("ES"), replacingDateES, "CGMES", "2D"))
                .thenReturn(List.of(new CaseInfos("20190501T1230Z_1D_REE_001.zip", uuidReplacingCaseES, "CGMES", "ES", "2D")));

        UUID uuidReplacingNetworkES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");
        Mockito.when(caseFetcherService.importCase(uuidReplacingCaseES))
                .thenReturn(uuidReplacingNetworkES);

        Map<String, IgmReplacingInfo> resReplacing = mergeOrchestratorService.replaceIGMs("SWE_2D", dateTime);

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
        assertEquals("SWE_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        List<IgmEntity> igmEntities = igmRepository.findAll();
        assertEquals(3, igmEntities.size());
        assertEquals(uuidReplacingNetworkES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("SWE_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(replacingDateES.toLocalDateTime(), igmEntities.get(0).getReplacingDate());
        assertEquals("2D", igmEntities.get(0).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("SWE_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertNull(igmEntities.get(1).getReplacingDate());
        assertNull(igmEntities.get(1).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_PT, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_FAILED", igmEntities.get(2).getStatus());
        assertEquals("SWE_2D", igmEntities.get(2).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertNull(igmEntities.get(2).getReplacingDate());
        assertNull(igmEntities.get(2).getReplacingBusinessProcess());

        // test message has been sent for ES igm
        Message<byte[]> messageEsIGM = output.receive(1000);
        assertEquals("AVAILABLE", messageEsIGM.getHeaders().get("status"));
        assertEquals("ES", messageEsIGM.getHeaders().get("tso"));
        assertEquals("SWE_2D", messageEsIGM.getHeaders().get("process"));
        messageEsIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messageEsIGM.getHeaders().get("status"));
        assertEquals("ES", messageEsIGM.getHeaders().get("tso"));
        assertEquals("SWE_2D", messageEsIGM.getHeaders().get("process"));

        // 2 - test replacing PT igm (at dateTime : 2019-05_01T17:30:00Z)
        UUID uuidReplacingCasePT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254f1");
        ZonedDateTime replacingDatePT = ZonedDateTime.of(2019, 5, 1, 17, 30, 0, 0, ZoneId.of("UTC"));

        Mockito.when(caseFetcherService.getCases(List.of("PT"), replacingDatePT, "CGMES", "2D"))
                .thenReturn(List.of(new CaseInfos("20190501T1730Z_1D_REN_001.zip", uuidReplacingCasePT, "CGMES", "PT", "2D")));

        UUID uuidReplacingNetworkPT = UUID.fromString("7928181c-7977-4592-ba19-88027e4254f2");
        Mockito.when(caseFetcherService.importCase(uuidReplacingCasePT))
                .thenReturn(uuidReplacingNetworkPT);

        resReplacing = mergeOrchestratorService.replaceIGMs("SWE_2D", dateTime);

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
        assertEquals("SWE_2D", mergeEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals("LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        igmEntities = igmRepository.findAll();
        assertEquals(3, igmEntities.size());
        assertEquals(uuidReplacingNetworkES, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals("SWE_2D", igmEntities.get(0).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(replacingDateES.toLocalDateTime(), igmEntities.get(0).getReplacingDate());
        assertEquals("2D", igmEntities.get(0).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals("SWE_2D", igmEntities.get(1).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertNull(igmEntities.get(1).getReplacingDate());
        assertNull(igmEntities.get(1).getReplacingBusinessProcess());

        assertEquals(uuidReplacingNetworkPT, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals("SWE_2D", igmEntities.get(2).getKey().getProcess());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(replacingDatePT.toLocalDateTime(), igmEntities.get(2).getReplacingDate());
        assertEquals("2D", igmEntities.get(2).getReplacingBusinessProcess());

        // test message has been sent for PT igm
        Message<byte[]> messagePtIGM = output.receive(1000);
        assertEquals("AVAILABLE", messagePtIGM.getHeaders().get("status"));
        assertEquals("PT", messagePtIGM.getHeaders().get("tso"));
        assertEquals("SWE_2D", messagePtIGM.getHeaders().get("process"));
        messagePtIGM = output.receive(1000);
        assertEquals("VALIDATION_SUCCEED", messagePtIGM.getHeaders().get("status"));
        assertEquals("PT", messagePtIGM.getHeaders().get("tso"));
        assertEquals("SWE_2D", messagePtIGM.getHeaders().get("process"));

        // test message has been sent for merge load flow
        Message<byte[]> messageMerge = output.receive(1000);
        assertEquals("LOADFLOW_SUCCEED", messageMerge.getHeaders().get("status"));
        assertEquals("SWE_2D", messageEsIGM.getHeaders().get("process"));
    }
}
