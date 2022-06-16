/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.ValidationException;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.merge.orchestrator.server.dto.*;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.gridsuite.merge.orchestrator.server.utils.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorException.Type.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * @author Jon Harper <jon.harper at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = WebEnvironment.MOCK)
@ContextHierarchy({@ContextConfiguration(classes = {MergeOrchestratorApplication.class, TestChannelBinderConfiguration.class})})
public class MergeOrchestratorIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergeOrchestratorIT.class);

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

    @Autowired
    BoundaryRepository boundaryRepository;

    @MockBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private CaseFetcherService caseFetcherService;

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

    private boolean runBalancesAdjustment;

    @Autowired
    private ObjectMapper mapper;

    private MockWebServer mockServer;

    private static final UUID UUID_CASE_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");

    private static final UUID UUID_CASE_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_NETWORK_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_CASE_ID_ES_VALIDATION_FAILED = UUID.fromString("0c36a4e9-3e91-4e4b-811d-b034e2f3d489");
    private static final UUID UUID_CASE_ID_ES_IMPORT_ERROR = UUID.fromString("0c36a4e9-3e91-4e4b-811d-b034e2f3d490");

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

    private static final String BOUNDARY_EQ_ID = "f1582c44-d9e2-4ea0-afdc-dba189ab4358";
    private static final String BOUNDARY_TP_ID = "3e3f7738-aab9-4284-a965-71d5cd151f71";

    private static final String SPECIFIC_BOUNDARY_EQ_ID = "66666666-d9e2-4ea0-afdc-dba189ab4358";
    private static final String SPECIFIC_BOUNDARY_TP_ID = "77777777-aab9-4284-a965-71d5cd151f71";

    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final NetworkFactory networkFactory = NetworkFactory.find("Default");
    private final ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 0, 0, 0, ZoneId.of("UTC"));

    private UUID reportUuid;
    private UUID reportErrorUuid;

    private void createProcessConfigs() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_1D_UUID, "SWE_1D", "1D", List.of("FR", "ES", "PT"), false, true, null, null));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_2D_UUID, "SWE_2D", "2D", List.of("FR", "ES", "PT"), false, true, null, null));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false, true, null, null));
    }

    private void createProcessConfigWithSpecificBoundaries() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_1D_UUID, "SWE_1D", "1D", List.of("FR", "ES", "PT"), false, false,
                new BoundaryInfo(SPECIFIC_BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", LocalDateTime.of(2021, 2, 10, 11, 0, 0)),
                new BoundaryInfo(SPECIFIC_BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", LocalDateTime.of(2021, 5, 20, 9, 30, 0))));
    }

    private void cleanDB() {
        processConfigRepository.deleteAll();
        igmRepository.deleteAll();
        mergeRepository.deleteAll();
        boundaryRepository.deleteAll();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_FR), any()))
                .thenReturn(UUID_NETWORK_ID_FR);
        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_ES), any()))
                .thenReturn(UUID_NETWORK_ID_ES);
        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_PT), any()))
                .thenReturn(UUID_NETWORK_ID_PT);
        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_PT_1), any()))
                .thenReturn(UUID_NETWORK_ID_PT_1);
        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_ES_VALIDATION_FAILED), any()))
                .thenReturn(UUID_CASE_ID_ES_VALIDATION_FAILED);
        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_ES_IMPORT_ERROR), any()))
                .thenThrow(new ValidationException(UUID_CASE_ID_ES_IMPORT_ERROR::toString, "Inconsistent voltage limit range"));

        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_FR, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("fr", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_ES, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("es", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_PT, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("pt", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_CASE_ID_ES_VALIDATION_FAILED, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("es", "iidm"));

        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_FR, UUID_NETWORK_ID_FR))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_ES, UUID_NETWORK_ID_ES))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_PT, UUID_NETWORK_ID_PT))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_PT_1, UUID_NETWORK_ID_PT_1))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_CASE_ID_ES_VALIDATION_FAILED, UUID_CASE_ID_ES_VALIDATION_FAILED))
                .thenReturn(false);
        Mockito.when(igmQualityCheckService.check(UUID_CASE_ID_ES_IMPORT_ERROR, UUID_CASE_ID_ES_IMPORT_ERROR))
                .thenReturn(false);

        Mockito.when(loadFlowService.run(any(), any()))
                .thenReturn(MergeStatus.FIRST_LOADFLOW_SUCCEED);

        initMockServer();

        cleanDB();
    }

    @After
    public void tearDown() {
        Set<String> httpRequest = null;
        try {
            httpRequest = getRequestsDone(1);
        } catch (NullPointerException e) {
            // Ignoring
        }

        // Shut down the server. Instances cannot be reused.
        try {
            mockServer.shutdown();
        } catch (Exception e) {
            // Ignoring
        }

        assertNull("Should not be any messages", output.receive(1000));
        assertNull("Should not be any http requests", httpRequest);
    }

    @SneakyThrows
    private void initMockServer() {
        mockServer = new MockWebServer();

        // FIXME: remove lines when dicos will be used on the front side
        mapper = new ObjectMapper();
        mapper.registerModule(new ReporterModelJsonModule());

        // Start the server.
        mockServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = mockServer.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        mergeOrchestratorConfigService.setReportServerBaseURI(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                //Buffer body = request.getBody();
                if (path.matches("/v1/reports/" + reportUuid) && "GET".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value()).setBody(mapper.writeValueAsString(REPORT_TEST))
                            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/reports/" + reportUuid) && "DELETE".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(HttpStatus.OK.value());
                } else if (path.matches("/v1/reports/" + reportErrorUuid)) {
                    return new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                } else {
                    LOGGER.error("Path not supported: " + request.getPath());
                    return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());

                }
            }
        };
        mockServer.setDispatcher(dispatcher);
    }

    private Set<String> getRequestsDone(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                return mockServer.takeRequest(0, TimeUnit.SECONDS).getPath();
            } catch (InterruptedException e) {
                LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    @Test
    public void testSingleMerge() {
        createProcessConfigs();

        // send first tso FR with business process = 1D, expect only one AVAILABLE and one VALIDATION_SUCCEED message
        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary eq"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary tp")));
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

        List<IgmEntity> igmEntities = mergeOrchestratorService.findAllIgms();
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

        igmEntities = mergeOrchestratorService.findAllIgms();
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(1).getNetworkUuid());
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
        // and one FIRST_LOADFLOW_SUCCEED message (merge done)
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
        assertEquals("FIRST_LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_1D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals("FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        assertTrue(mergeOrchestratorService.getMerges(FOO_1D_UUID).isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges(SWE_1D_UUID);
        assertEquals(1, mergeInfos.size());
        assertEquals(SWE_1D_UUID, mergeInfos.get(0).getProcessUuid());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertFalse(mergeOrchestratorService.getMerges(SWE_1D_UUID, dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));
    }

    private void testErrorMessage(String errorMessage) {
        assertEquals(errorMessage, output.receive(1000).getHeaders().get("error"));
    }

    @Test
    public void testMergeWithSpecificBoundaries() {
        createProcessConfigWithSpecificBoundaries();

        // first specific boundary available
        Mockito.when(cgmesBoundaryService.getBoundary(SPECIFIC_BOUNDARY_EQ_ID))
                .thenReturn(Optional.of(new BoundaryContent(SPECIFIC_BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary 1")));

        // error when second specific boundary is unavailable
        Mockito.when(caseFetcherService.getCases(any(), any(), any(), any()))
                .thenReturn(List.of(new CaseInfos("fr", UUID_CASE_ID_FR, "", "FR", "1D")));
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "1D")
                .build());
        assertEquals("AVAILABLE", output.receive(1000).getHeaders().get("status"));
        testErrorMessage("Process SWE_1D (1D) : EQ and/or TP boundary not available !!");
        assertEquals("VALIDATION_FAILED", output.receive(1000).getHeaders().get("status"));

        // second specific boundary is now available
        Mockito.when(cgmesBoundaryService.getBoundary(SPECIFIC_BOUNDARY_TP_ID))
                .thenReturn(Optional.of(new BoundaryContent(SPECIFIC_BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary 2")));

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
        testImportIgmMessages(1, false);

        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_1D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        List<IgmEntity> igmEntities = mergeOrchestratorService.findAllIgms();
        assertEquals(1, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals("FR", igmEntities.get(0).getKey().getTso());
        assertEquals(UUID_CASE_ID_FR, igmEntities.get(0).getCaseUuid());
        assertNull(igmEntities.get(0).getReplacingDate());
        assertNull(igmEntities.get(0).getReplacingBusinessProcess());
        assertEquals(SPECIFIC_BOUNDARY_EQ_ID, igmEntities.get(0).getEqBoundary());
        assertEquals(SPECIFIC_BOUNDARY_TP_ID, igmEntities.get(0).getTpBoundary());

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
        testImportIgmMessages(1, false);

        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_1D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        igmEntities = mergeOrchestratorService.findAllIgms();
        assertEquals(2, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals("ES", igmEntities.get(1).getKey().getTso());
        assertEquals(UUID_CASE_ID_ES, igmEntities.get(1).getCaseUuid());
        assertNull(igmEntities.get(1).getReplacingDate());
        assertNull(igmEntities.get(1).getReplacingBusinessProcess());
        assertEquals(SPECIFIC_BOUNDARY_EQ_ID, igmEntities.get(1).getEqBoundary());
        assertEquals(SPECIFIC_BOUNDARY_TP_ID, igmEntities.get(1).getTpBoundary());

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
        testImportIgmMessages(1, true);
        testMergeOk(mergeOrchestratorService.getMerges(SWE_1D_UUID).get(0), List.of("FR", "ES", "PT"));

        igmEntities = mergeOrchestratorService.findAllIgms();
        assertEquals(3, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_PT, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals(SWE_1D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals("PT", igmEntities.get(2).getKey().getTso());
        assertEquals(UUID_CASE_ID_PT, igmEntities.get(2).getCaseUuid());
        assertNull(igmEntities.get(2).getReplacingDate());
        assertNull(igmEntities.get(2).getReplacingBusinessProcess());
        assertEquals(SPECIFIC_BOUNDARY_EQ_ID, igmEntities.get(2).getEqBoundary());
        assertEquals(SPECIFIC_BOUNDARY_TP_ID, igmEntities.get(2).getTpBoundary());

        assertNull(output.receive(1000));
    }

    @Test
    public void testMultipleMerge() {
        createProcessConfigs();

        // send first tso FR with business process = 2D, expect two AVAILABLE and two VALIDATION_SUCCEED message
        // (for both process SWE_2D and FRES_2D)
        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary eq"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary tp")));
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

        String reportFres2Duuid = mergeEntities.get(0).getReportUUID().toString();
        String reportSwe2Duuid = mergeEntities.get(1).getReportUUID().toString();

        List<IgmEntity> igmEntities = mergeOrchestratorService.findAllIgms();
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
        assertEquals("FIRST_LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertEquals(FRES_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals("FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, mergeEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertNull(mergeEntities.get(1).getStatus());

        igmEntities = mergeOrchestratorService.findAllIgms();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(4, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(FRES_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(FRES_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(UUID_NETWORK_ID_ES, igmEntities.get(3).getNetworkUuid());
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
        assertEquals("FIRST_LOADFLOW_SUCCEED", messageMergeStarted.getHeaders().get("status"));

        mergeEntities = mergeRepository.findAll();
        mergeEntities.sort(Comparator.comparing(merge -> merge.getKey().getProcessUuid()));
        assertEquals(2, mergeEntities.size());
        assertEquals(FRES_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals("FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, mergeEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(1).getKey().getDate());
        assertEquals("FIRST_LOADFLOW_SUCCEED", mergeEntities.get(1).getStatus());

        assertTrue(mergeOrchestratorService.getMerges(FOO_1D_UUID).isEmpty());
        List<Merge> mergeInfos = mergeOrchestratorService.getMerges(SWE_1D_UUID);
        assertEquals(0, mergeInfos.size());
        mergeInfos = mergeOrchestratorService.getMerges(SWE_2D_UUID);
        assertEquals(1, mergeInfos.size());
        assertEquals(SWE_2D_UUID, mergeInfos.get(0).getProcessUuid());
        assertEquals(runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED, mergeInfos.get(0).getStatus());
        assertEquals(dateTime.toLocalDateTime(), mergeInfos.get(0).getDate().toLocalDateTime());
        assertEquals(3, mergeInfos.get(0).getIgms().size());
        assertEquals("FR", mergeInfos.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(0).getStatus());
        assertEquals("ES", mergeInfos.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(1).getStatus());
        assertEquals("PT", mergeInfos.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, mergeInfos.get(0).getIgms().get(2).getStatus());

        assertTrue(mergeOrchestratorService.getMerges(SWE_1D_UUID, dateTime, dateTime).isEmpty());
        assertFalse(mergeOrchestratorService.getMerges(SWE_2D_UUID, dateTime, dateTime).isEmpty());
        assertFalse(mergeOrchestratorService.getMerges(FRES_2D_UUID, dateTime, dateTime).isEmpty());

        assertNull(output.receive(1000));
        // test delete config
        List<MergeEntity> merges = mergeRepository.findAll();
        assertEquals(2, merges.size());
        assertEquals("[MergeEntity(key=MergeEntityKey(processUuid=" + SWE_2D_UUID + ", date=2019-05-01T09:00), status=FIRST_LOADFLOW_SUCCEED, reportUUID=" + reportSwe2Duuid + "), MergeEntity(key=MergeEntityKey(processUuid=" + FRES_2D_UUID + ", date=2019-05-01T09:00), status=FIRST_LOADFLOW_SUCCEED, reportUUID=" + reportFres2Duuid + ")]",
                merges.toString());
        assertEquals("[IgmEntity(key=IgmEntityKey(processUuid=" + SWE_2D_UUID + ", date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_FR + ", caseUuid=" + UUID_CASE_ID_FR + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + "), IgmEntity(key=IgmEntityKey(processUuid=" + FRES_2D_UUID + ", date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_FR + ", caseUuid=" + UUID_CASE_ID_FR + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + "), IgmEntity(key=IgmEntityKey(processUuid=" + SWE_2D_UUID + ", date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_ES + ", caseUuid=" + UUID_CASE_ID_ES + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + "), IgmEntity(key=IgmEntityKey(processUuid=" + FRES_2D_UUID + ", date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_ES + ", caseUuid=" + UUID_CASE_ID_ES + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + "), IgmEntity(key=IgmEntityKey(processUuid=" + SWE_2D_UUID + ", date=2019-05-01T09:00, tso=PT), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_PT + ", caseUuid=" + UUID_CASE_ID_PT + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + ")]",
                mergeOrchestratorService.findAllIgms().toString());

        reportUuid = merges.get(0).getReportUUID();
        mergeOrchestratorConfigService.deleteConfig(SWE_2D_UUID);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        assertEquals("[MergeEntity(key=MergeEntityKey(processUuid=" + FRES_2D_UUID + ", date=2019-05-01T09:00), status=FIRST_LOADFLOW_SUCCEED, reportUUID=" + reportFres2Duuid + ")]",
                mergeRepository.findAll().toString());
        assertEquals("[IgmEntity(key=IgmEntityKey(processUuid=" + FRES_2D_UUID + ", date=2019-05-01T09:00, tso=FR), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_FR + ", caseUuid=" + UUID_CASE_ID_FR + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + "), IgmEntity(key=IgmEntityKey(processUuid=" + FRES_2D_UUID + ", date=2019-05-01T09:00, tso=ES), status=VALIDATION_SUCCEED, networkUuid=" + UUID_NETWORK_ID_ES + ", caseUuid=" + UUID_CASE_ID_ES + ", replacingDate=null, replacingBusinessProcess=null, eqBoundary=" + BOUNDARY_EQ_ID + ", tpBoundary=" + BOUNDARY_TP_ID + ")]",
                mergeOrchestratorService.findAllIgms().toString());

        ArrayList<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_2D_UUID, "SWE_2D", "2D", tsos, false, true, null, null));
    }

    private void testImportIgmMessages(int nbOfTimes, boolean withMerge) {
        testImportIgmMessages(nbOfTimes, withMerge, true);
    }

    private void testImportIgmMessages(int nbOfTimes, boolean withMerge, boolean withValidationSucceed) {
        IntStream.range(0, nbOfTimes).forEach(i ->
                assertEquals("AVAILABLE", output.receive(1000).getHeaders().get("status"))
        );

        IntStream.range(0, nbOfTimes).forEach(i ->
                assertEquals(withValidationSucceed ? "VALIDATION_SUCCEED" : "VALIDATION_FAILED", output.receive(1000).getHeaders().get("status"))
        );

        if (withMerge) {
            assertEquals("FIRST_LOADFLOW_SUCCEED", output.receive(1000).getHeaders().get("status"));
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
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false, true, null, null));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRPT_2D_UUID, "FRPT_2D", "2D", List.of("FR", "PT"), false, true, null, null));

        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary 1"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary 2")));
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 0, 0, 0, ZoneId.of("UTC"));
        String mergeDate = DATE_FORMATTER.format(dateTime);

        // send tsos FR, ES and PT with business process = 2D
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", mergeDate)
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", mergeDate)
                .setHeader("uuid", UUID_CASE_ID_ES.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "PT")
                .setHeader("date", mergeDate)
                .setHeader("uuid", UUID_CASE_ID_PT.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        testImportIgmMessages(2, false);
        testImportIgmMessages(1, true);
        testImportIgmMessages(1, true);

        testMergeOk(mergeOrchestratorService.getMerges(FRES_2D_UUID).get(0), List.of("FR", "ES"));
        testMergeOk(mergeOrchestratorService.getMerges(FRPT_2D_UUID).get(0), List.of("FR", "PT"));

        List<MergeEntity> merges = mergeRepository.findAll();
        assertEquals(2, merges.size());
        assertEquals(4, igmRepository.findAll().size());

        UUID randomUuid = UUID.randomUUID();
        LocalDateTime dateNow = LocalDateTime.now();
        assertTrue(assertThrows(MergeOrchestratorException.class, () -> mergeOrchestratorService.getReport(randomUuid, dateNow))
                .getMessage().contains(MERGE_NOT_FOUND.name()));

        assertTrue(assertThrows(MergeOrchestratorException.class, () -> mergeOrchestratorConfigService.getReport(randomUuid))
                .getMessage().contains(MERGE_REPORT_NOT_FOUND.name()));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", randomUuid)));

        reportErrorUuid = UUID.randomUUID();
        assertTrue(assertThrows(MergeOrchestratorException.class, () -> mergeOrchestratorConfigService.getReport(reportErrorUuid))
                .getMessage().contains(MERGE_REPORT_ERROR.name()));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportErrorUuid)));

        reportUuid = merges.get(0).getReportUUID();
        assertThat(mergeOrchestratorConfigService.getReport(reportUuid), new MatcherReport(REPORT_TEST));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        reportUuid = merges.get(1).getReportUUID();
        assertThat(mergeOrchestratorConfigService.getReport(reportUuid), new MatcherReport(REPORT_TEST));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        assertTrue(assertThrows(MergeOrchestratorException.class, () -> mergeOrchestratorService.deleteReport(randomUuid, dateNow))
                .getMessage().contains(MERGE_NOT_FOUND.name()));

        assertTrue(assertThrows(MergeOrchestratorException.class, () -> mergeOrchestratorConfigService.deleteReport(randomUuid))
                .getMessage().contains(MERGE_REPORT_NOT_FOUND.name()));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", randomUuid)));

        assertTrue(assertThrows(MergeOrchestratorException.class, () -> mergeOrchestratorConfigService.deleteReport(reportErrorUuid))
                .getMessage().contains(MERGE_REPORT_ERROR.name()));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportErrorUuid)));

        reportUuid = merges.get(0).getReportUUID();
        mergeOrchestratorService.deleteReport(FRES_2D_UUID, LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        reportUuid = merges.get(1).getReportUUID();
        mergeOrchestratorService.deleteReport(FRPT_2D_UUID, LocalDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC));
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        reportUuid = merges.get(0).getReportUUID();
        mergeOrchestratorConfigService.deleteConfig(FRES_2D_UUID);
        assertEquals(1, mergeRepository.findAll().size());
        assertEquals(2, igmRepository.findAll().size());
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        reportUuid = merges.get(1).getReportUUID();
        mergeOrchestratorConfigService.deleteConfig(FRPT_2D_UUID);
        assertEquals(0, mergeRepository.findAll().size());
        assertEquals(0, igmRepository.findAll().size());
        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        assertNull(output.receive(1000));
    }

    @Test
    public void testDeleteIgmMergeWithImportError() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false, true, null, null));

        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary 1"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary 2")));

        // send tsos FR, ES and PT with business process = 2D
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // send tso ES with import network error
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES_IMPORT_ERROR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        testImportIgmMessages(1, false);
        testImportIgmMessages(1, false, false);

        List<MergeEntity> merges = mergeRepository.findAll();
        assertEquals(1, merges.size());
        assertEquals(2, igmRepository.findAll().size());

        reportUuid = merges.get(0).getReportUUID();
        mergeOrchestratorConfigService.deleteConfig(FRES_2D_UUID);
        assertEquals(0, mergeRepository.findAll().size());
        assertEquals(0, igmRepository.findAll().size());

        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        assertNull(output.receive(1000));
    }

    @Test
    public void testDeleteIgmMergeWithValidationFailed() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false, true, null, null));

        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary 1"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary 2")));

        // send tsos FR, ES and PT with business process = 2D
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "FR")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_FR.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        // send tso ES with import network error
        input.send(MessageBuilder.withPayload("")
                .setHeader("tso", "ES")
                .setHeader("date", "2019-05-01T10:00:00.000+01:00")
                .setHeader("uuid", UUID_CASE_ID_ES_VALIDATION_FAILED.toString())
                .setHeader("format", "CGMES")
                .setHeader("businessProcess", "2D")
                .build());

        testImportIgmMessages(1, false);
        testImportIgmMessages(1, false, false);

        List<MergeEntity> merges = mergeRepository.findAll();
        assertEquals(1, mergeRepository.findAll().size());
        assertEquals(2, igmRepository.findAll().size());

        reportUuid = merges.get(0).getReportUUID();
        mergeOrchestratorConfigService.deleteConfig(FRES_2D_UUID);
        assertEquals(0, mergeRepository.findAll().size());
        assertEquals(0, igmRepository.findAll().size());

        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", reportUuid)));

        assertNull(output.receive(1000));
    }

    @Test
    public void testImportIgmByOnlyMatchingConfigs() {
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false, true, null, null));
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(FRPT_2D_UUID, "FRPT_2D", "2D", List.of("FR", "PT"), false, true, null, null));
        MergeStatus mergeStatusOk = runBalancesAdjustment ? MergeStatus.BALANCE_ADJUSTMENT_SUCCEED : MergeStatus.FIRST_LOADFLOW_SUCCEED;

        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary 1"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary 2")));

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

        List<IgmEntity> igmEntities = mergeOrchestratorService.findAllIgms();
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

        igmEntities = mergeOrchestratorService.findAllIgms();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(3, igmEntities.size());
        assertThat(igmEntities.get(1),
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

        igmEntities = mergeOrchestratorService.findAllIgms();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(4, igmEntities.size());
        assertThat(igmEntities.get(3),
                new MatcherIgmEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), "PT", IgmStatus.VALIDATION_SUCCEED, UUID_NETWORK_ID_PT));

        List<Merge> mergeInfos = mergeOrchestratorService.getMerges(FRES_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("FR", "ES"));

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

        igmEntities = mergeOrchestratorService.findAllIgms();
        igmEntities.sort(Comparator.comparing(igm -> igm.getKey().getProcessUuid()));
        assertEquals(4, igmEntities.size());
        assertThat(igmEntities.get(3),
                new MatcherIgmEntity(FRPT_2D_UUID, dateTime.toLocalDateTime(), "PT", IgmStatus.VALIDATION_SUCCEED, UUID_CASE_ID_PT_1));

        mergeInfos = mergeOrchestratorService.getMerges(FRES_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("FR", "ES"));

        mergeInfos = mergeOrchestratorService.getMerges(FRPT_2D_UUID);
        assertEquals(1, mergeInfos.size());
        testMergeOk(mergeInfos.get(0), List.of("FR", "PT"));

        assertNull(output.receive(1000));
    }

    @Test
    public void parametersRepositoryTest() {
        createProcessConfigs();
        List<ProcessConfig> configs = mergeOrchestratorConfigService.getConfigs();
        assertEquals(3, configs.size());
        List<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(XYZ_2D_UUID, "XYZ_2D", "2D", tsos, true, true, null, null));
        configs = mergeOrchestratorConfigService.getConfigs();
        assertEquals(4, configs.size());

        assertTrue(mergeOrchestratorConfigService.getConfig(SWE_1D_UUID).isPresent());
        assertTrue(mergeOrchestratorConfigService.getConfig(SWE_2D_UUID).isPresent());
        assertTrue(mergeOrchestratorConfigService.getConfig(FRES_2D_UUID).isPresent());
        assertTrue(mergeOrchestratorConfigService.getConfig(XYZ_2D_UUID).isPresent());
        assertEquals(SWE_1D_UUID, mergeOrchestratorConfigService.getConfig(SWE_1D_UUID).get().getProcessUuid());
        assertEquals("1D", mergeOrchestratorConfigService.getConfig(SWE_1D_UUID).get().getBusinessProcess());
        assertEquals(SWE_2D_UUID, mergeOrchestratorConfigService.getConfig(SWE_2D_UUID).get().getProcessUuid());
        assertEquals("2D", mergeOrchestratorConfigService.getConfig(SWE_2D_UUID).get().getBusinessProcess());
        assertEquals(FRES_2D_UUID, mergeOrchestratorConfigService.getConfig(FRES_2D_UUID).get().getProcessUuid());
        assertEquals("2D", mergeOrchestratorConfigService.getConfig(FRES_2D_UUID).get().getBusinessProcess());
        assertEquals(XYZ_2D_UUID, mergeOrchestratorConfigService.getConfig(XYZ_2D_UUID).get().getProcessUuid());
        assertEquals("2D", mergeOrchestratorConfigService.getConfig(XYZ_2D_UUID).get().getBusinessProcess());
        assertFalse(mergeOrchestratorConfigService.getConfig(SWE_1D_UUID).get().isRunBalancesAdjustment());
        assertFalse(mergeOrchestratorConfigService.getConfig(SWE_2D_UUID).get().isRunBalancesAdjustment());
        assertFalse(mergeOrchestratorConfigService.getConfig(FRES_2D_UUID).get().isRunBalancesAdjustment());
        assertTrue(mergeOrchestratorConfigService.getConfig(XYZ_2D_UUID).get().isRunBalancesAdjustment());
        assertEquals(3, mergeOrchestratorConfigService.getConfig(SWE_1D_UUID).get().getTsos().size());
        assertEquals(3, mergeOrchestratorConfigService.getConfig(SWE_2D_UUID).get().getTsos().size());
        assertEquals(2, mergeOrchestratorConfigService.getConfig(FRES_2D_UUID).get().getTsos().size());
        assertEquals(2, mergeOrchestratorConfigService.getConfig(XYZ_2D_UUID).get().getTsos().size());
    }

    @Test
    public void replacingIGMsTest() {
        // process dateTime : 2019-05_01T09:30:00Z
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 9, 30, 0, 0, ZoneId.of("UTC"));

        ArrayList<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        mergeOrchestratorConfigService.addConfig(new ProcessConfig(SWE_2D_UUID, "SWE_2D", "2D", tsos, false, true, null, null));

        // init incomplete merge and merge_igm data in database : missing ES and invalid PT igms
        mergeRepository.save(new MergeEntity(new MergeEntityKey(SWE_2D_UUID, dateTime.toLocalDateTime()), null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_2D_UUID, dateTime.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_ID_FR, null, null, null, null, null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_2D_UUID, dateTime.toLocalDateTime(), "PT"), IgmStatus.VALIDATION_FAILED.name(), UUID_NETWORK_ID_PT, null, null, null, null, null));

        Mockito.when(cgmesBoundaryService.getLastBoundaries())
                .thenReturn(List.of(new BoundaryContent(BOUNDARY_EQ_ID, "20210315T0000Z__ENTSOE_EQBD_002.xml", "fake content for boundary eq"),
                        new BoundaryContent(BOUNDARY_TP_ID, "20210315T0000Z__ENTSOE_TPBD_002.xml", "fake content for boundary tp")));

        // 1 - test replacing ES igm (at dateTime : 2019-05_01T12:30:00Z)
        //
        UUID uuidReplacingCaseES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        ZonedDateTime replacingDateES = ZonedDateTime.of(2019, 5, 1, 12, 30, 0, 0, ZoneId.of("UTC"));

        Mockito.when(caseFetcherService.getCases(List.of("ES"), replacingDateES, "CGMES", "2D"))
                .thenReturn(List.of(new CaseInfos("20190501T1230Z_1D_REE_001.zip", uuidReplacingCaseES, "CGMES", "ES", "2D")));

        UUID uuidReplacingNetworkES = UUID.fromString("11111111-7977-4592-ba19-88027e4254e6");
        Mockito.when(networkConversionService.importCase(eq(uuidReplacingCaseES), any()))
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

        // test merge_igm replacement has been done for ES igm in database
        List<MergeEntity> mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertNull(mergeEntities.get(0).getStatus());

        List<IgmEntity> igmEntities = mergeOrchestratorService.findAllIgms();
        assertEquals(3, igmEntities.size());

        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertNull(igmEntities.get(0).getReplacingDate());
        assertNull(igmEntities.get(0).getReplacingBusinessProcess());

        assertEquals(UUID_NETWORK_ID_PT, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_FAILED", igmEntities.get(1).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertNull(igmEntities.get(1).getReplacingDate());
        assertNull(igmEntities.get(1).getReplacingBusinessProcess());

        assertEquals(uuidReplacingNetworkES, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(replacingDateES.toLocalDateTime(), igmEntities.get(2).getReplacingDate());
        assertEquals("2D", igmEntities.get(2).getReplacingBusinessProcess());

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
        Mockito.when(networkConversionService.importCase(eq(uuidReplacingCasePT), any()))
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

        // test merge_igm replacement has been done for PT igm in database
        mergeEntities = mergeRepository.findAll();
        assertEquals(1, mergeEntities.size());
        assertEquals(SWE_2D_UUID, mergeEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), mergeEntities.get(0).getKey().getDate());
        assertEquals("FIRST_LOADFLOW_SUCCEED", mergeEntities.get(0).getStatus());

        igmEntities = mergeOrchestratorService.findAllIgms();
        assertEquals(3, igmEntities.size());
        assertEquals(UUID_NETWORK_ID_FR, igmEntities.get(0).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(0).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(0).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(0).getKey().getDate());
        assertNull(igmEntities.get(0).getReplacingDate());
        assertNull(igmEntities.get(0).getReplacingBusinessProcess());

        assertEquals(uuidReplacingNetworkPT, igmEntities.get(1).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(1).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(1).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(1).getKey().getDate());
        assertEquals(replacingDatePT.toLocalDateTime(), igmEntities.get(1).getReplacingDate());
        assertEquals("2D", igmEntities.get(1).getReplacingBusinessProcess());

        assertEquals(uuidReplacingNetworkES, igmEntities.get(2).getNetworkUuid());
        assertEquals("VALIDATION_SUCCEED", igmEntities.get(2).getStatus());
        assertEquals(SWE_2D_UUID, igmEntities.get(2).getKey().getProcessUuid());
        assertEquals(dateTime.toLocalDateTime(), igmEntities.get(2).getKey().getDate());
        assertEquals(replacingDateES.toLocalDateTime(), igmEntities.get(2).getReplacingDate());
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

    @Test
    public void testGetMerges() {
        ZonedDateTime dateTime = ZonedDateTime.of(2020, 7, 20, 8, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime replacingTime1 = ZonedDateTime.of(2020, 7, 21, 5, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime replacingTime2 = ZonedDateTime.of(2020, 7, 22, 12, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime replacingTime3 = ZonedDateTime.of(2020, 7, 15, 17, 30, 0, 0, ZoneId.of("UTC"));

        mergeRepository.save(new MergeEntity(new MergeEntityKey(SWE_1D_UUID, dateTime.toLocalDateTime()), MergeStatus.FIRST_LOADFLOW_SUCCEED.name()));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime.toLocalDateTime(), "FR"), IgmStatus.AVAILABLE.name(), UUID_NETWORK_ID_FR, UUID_CASE_ID_FR, replacingTime1.toLocalDateTime(), "RT", null, null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime.toLocalDateTime(), "ES"), IgmStatus.VALIDATION_FAILED.name(), UUID_NETWORK_ID_ES, UUID_CASE_ID_ES, replacingTime2.toLocalDateTime(), "2D", null, null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime.toLocalDateTime(), "PT"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_ID_PT, UUID_CASE_ID_PT, replacingTime3.toLocalDateTime(), "YR", null, null));

        // test without date interval
        List<Merge> merges = mergeOrchestratorService.getMerges(SWE_2D_UUID);
        assertTrue(merges.isEmpty());

        merges = mergeOrchestratorService.getMerges(SWE_1D_UUID);
        assertEquals(1, merges.size());
        assertEquals(SWE_1D_UUID, merges.get(0).getProcessUuid());
        assertEquals(dateTime, merges.get(0).getDate());
        assertEquals(MergeStatus.FIRST_LOADFLOW_SUCCEED, merges.get(0).getStatus());
        assertEquals(3, merges.get(0).getIgms().size());
        assertEquals("FR", merges.get(0).getIgms().get(0).getTso());
        assertEquals(IgmStatus.AVAILABLE, merges.get(0).getIgms().get(0).getStatus());
        assertEquals(replacingTime1, merges.get(0).getIgms().get(0).getReplacingDate());
        assertEquals("RT", merges.get(0).getIgms().get(0).getReplacingBusinessProcess());
        assertEquals("ES", merges.get(0).getIgms().get(1).getTso());
        assertEquals(IgmStatus.VALIDATION_FAILED, merges.get(0).getIgms().get(1).getStatus());
        assertEquals(replacingTime2, merges.get(0).getIgms().get(1).getReplacingDate());
        assertEquals("2D", merges.get(0).getIgms().get(1).getReplacingBusinessProcess());
        assertEquals("PT", merges.get(0).getIgms().get(2).getTso());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, merges.get(0).getIgms().get(2).getStatus());
        assertEquals(replacingTime3, merges.get(0).getIgms().get(2).getReplacingDate());
        assertEquals("YR", merges.get(0).getIgms().get(2).getReplacingBusinessProcess());

        // test with date interval
        ZonedDateTime minDate = ZonedDateTime.of(2020, 7, 20, 12, 30, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime maxDate = ZonedDateTime.of(2020, 7, 20, 15, 30, 0, 0, ZoneId.of("UTC"));
        merges = mergeOrchestratorService.getMerges(SWE_1D_UUID, minDate, maxDate);
        assertTrue(merges.isEmpty());

        minDate = ZonedDateTime.of(2020, 7, 20, 6, 30, 0, 0, ZoneId.of("UTC"));
        maxDate = ZonedDateTime.of(2020, 7, 20, 15, 30, 0, 0, ZoneId.of("UTC"));
        merges = mergeOrchestratorService.getMerges(SWE_1D_UUID, minDate, maxDate);
        assertEquals(1, merges.size());
    }
}
