/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import lombok.SneakyThrows;
import org.gridsuite.merge.orchestrator.server.dto.*;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.merge.orchestrator.server.MergeOrchestratorConstants.DELIMITER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = {MergeOrchestratorApplication.class, TestChannelBinderConfiguration.class})
public class MergeOrchestratorControllerTest {

    private static final UUID UUID_NETWORK = UUID.fromString("db9b8260-0e8d-4e0c-aad4-56994c151925");
    private static final UUID UUID_NETWORK_MERGE_1 = UUID.fromString("a7a38a4c-a733-4d5e-a38d-8c6ab121c497");
    private static final UUID UUID_NETWORK_MERGE_2 = UUID.fromString("2218c9b7-8fe9-4c1e-b8fa-d75582563917");
    private static final UUID UUID_NETWORK_MERGE_3 = UUID.fromString("eb801df9-c2ea-4291-819f-aed73cd25aea");
    private static final UUID UUID_CASE = UUID.fromString("da4a9da3-b517-4f55-b7e8-ad12b10b60b3");
    private static final UUID UUID_CASE_MERGE_1 = UUID.fromString("425131e8-3d5e-4584-a63b-10812b6ba2b8");
    private static final UUID UUID_CASE_MERGE_2 = UUID.fromString("1dc86636-b886-4b39-8283-d9643bbedc7c");
    private static final UUID UUID_CASE_MERGE_3 = UUID.fromString("872923ca-9a9a-439b-8a77-20795a2791df");

    private static final UUID SWE_1D_UUID = UUID.fromString("11111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID SWE_2D_UUID = UUID.fromString("21111111-f60e-4766-bc5c-8f312c1984e4");

    private static final UUID FRES_2D_UUID = UUID.fromString("21111111-f60e-4766-bc5c-8f312c1984e4");
    private static final UUID UUID_CASE_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_CASE_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
    private static final UUID UUID_NETWORK_ID_FR = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK_ID_ES = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Autowired
    private MockMvc mvc;

    @Autowired
    MergeRepository mergeRepository;

    @Autowired
    IgmRepository igmRepository;

    @Autowired
    ProcessConfigRepository processConfigRepository;

    @MockitoBean
    private NetworkStoreService networkStoreService;

    @MockitoBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockitoBean
    private CgmesBoundaryService cgmesBoundaryService;

    @MockitoBean
    private LoadFlowService loadFlowService;

    @MockitoBean
    private NetworkConversionService networkConversionService;

    @Autowired
    private MergeOrchestratorConfigService mergeConfigService;

    private final NetworkFactory networkFactory = NetworkFactory.find("Default");

    private MockRestServiceServer mockReportServer;

    @Autowired
    InputDestination input;

    private void cleanDB() {
        igmRepository.deleteAll();
        mergeRepository.deleteAll();
        processConfigRepository.deleteAll();
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mockReportServer = MockRestServiceServer.createServer(mergeConfigService.getReportRestClient());

        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_FR), any()))
                .thenReturn(UUID_NETWORK_ID_FR);
        Mockito.when(networkConversionService.importCase(eq(UUID_CASE_ID_ES), any()))
                .thenReturn(UUID_NETWORK_ID_ES);

        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_FR, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("fr", "iidm"));
        Mockito.when(networkStoreService.getNetwork(UUID_NETWORK_ID_ES, PreloadingStrategy.COLLECTION))
                .thenReturn(networkFactory.createNetwork("es", "iidm"));

        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_FR, UUID_NETWORK_ID_FR))
                .thenReturn(true);
        Mockito.when(igmQualityCheckService.check(UUID_NETWORK_ID_ES, UUID_NETWORK_ID_ES))
                .thenReturn(true);

        Mockito.when(loadFlowService.run(any(), any()))
                .thenReturn(MergeStatus.FIRST_LOADFLOW_SUCCEED);

        cleanDB();
    }

    @Test
    public void test() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.of(2020, 7, 20, 10, 0, 0, 0, ZoneId.of("UTC"));
        mergeRepository.save(new MergeEntity(new MergeEntityKey(SWE_1D_UUID, dateTime.toLocalDateTime()), MergeStatus.FIRST_LOADFLOW_SUCCEED.name()));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK, UUID_CASE, null, null, null, null));

        String resExpected = "[{\"processUuid\":\"" + SWE_1D_UUID + "\",\"date\":\"" + DATE_FORMATTER.format(dateTime) + "\",\"status\":\"FIRST_LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}]";

        mvc.perform(get("/" + VERSION + "/" + SWE_1D_UUID + "/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        mvc.perform(get("/" + VERSION + "/" + SWE_2D_UUID + "/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        mvc.perform(get("/" + VERSION + "/" + UUID.randomUUID() + "/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        String date = URLEncoder.encode(DATE_FORMATTER.format(dateTime), StandardCharsets.UTF_8);
        mvc.perform(get("/" + VERSION + "/" + SWE_1D_UUID + "/merges?minDate=" + date + "&maxDate=" + date)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        ZonedDateTime dateTime2 = ZonedDateTime.of(2020, 7, 20, 10, 30, 0, 0, ZoneId.of("UTC"));
        mergeRepository.save(new MergeEntity(new MergeEntityKey(SWE_1D_UUID, dateTime2.toLocalDateTime()), MergeStatus.FIRST_LOADFLOW_SUCCEED.name()));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime2.toLocalDateTime(), "ES"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK, UUID_CASE, null, null, null, null));
        String resExpected2 = "[{\"processUuid\":\"" + SWE_1D_UUID + "\",\"date\":\"2020-07-20T10:00:00Z\",\"status\":\"FIRST_LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}]";
        mvc.perform(get("/" + VERSION + "/" + SWE_1D_UUID + "/merges?minDate=" + date + "&maxDate=" + date)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected2));

        ZonedDateTime minDateTime = ZonedDateTime.of(2020, 7, 20, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime maxDateTime = ZonedDateTime.of(2020, 7, 20, 12, 0, 0, 0, ZoneId.of("UTC"));
        String minDate = URLEncoder.encode(DATE_FORMATTER.format(minDateTime), StandardCharsets.UTF_8);
        String maxDate = URLEncoder.encode(DATE_FORMATTER.format(maxDateTime), StandardCharsets.UTF_8);
        String resExpected3 = "[{\"processUuid\":\"" + SWE_1D_UUID + "\",\"date\":\"2020-07-20T10:00:00Z\",\"status\":\"FIRST_LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}," +
                "{\"processUuid\":\"" + SWE_1D_UUID + "\",\"date\":\"2020-07-20T10:30:00Z\",\"status\":\"FIRST_LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"ES\",\"status\":\"VALIDATION_SUCCEED\"}]}]";
        mvc.perform(get("/" + VERSION + "/" + SWE_1D_UUID + "/merges?minDate=" + minDate + "&maxDate=" + maxDate)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected3));

        mergeConfigService.addConfig(new ProcessConfig(SWE_1D_UUID, "FRESPT_2D", "2D", List.of("FR", "ES", "PT"), false, true, null, null));
        ZonedDateTime dateTime3 = ZonedDateTime.of(2020, 7, 20, 10, 30, 0, 0, ZoneId.of("UTC"));
        mergeRepository.save(new MergeEntity(new MergeEntityKey(SWE_1D_UUID, dateTime3.toLocalDateTime()), MergeStatus.FIRST_LOADFLOW_SUCCEED.name()));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime3.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_MERGE_1, UUID_CASE_MERGE_1, null, null, null, null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime3.toLocalDateTime(), "ES"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_MERGE_2, UUID_CASE_MERGE_2, null, null, null, null));
        igmRepository.save(new IgmEntity(new IgmEntityKey(SWE_1D_UUID, dateTime3.toLocalDateTime(), "PT"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_MERGE_3, UUID_CASE_MERGE_3, null, null, null, null));
        String processDate = URLEncoder.encode(DATE_FORMATTER.format(dateTime3), StandardCharsets.UTF_8);
        given(cgmesBoundaryService.getLastBoundaries())
                .willReturn(List.of(new BoundaryContent("idEQ", "EQ_boundary.xml", "fake eq boundary"), new BoundaryContent("idTP", "TP_boundary.xml", "fake tp boundary")));
        given(networkConversionService.exportMerge(any(List.class), any(List.class), any(String.class), any(String.class), any(List.class)))
                .willReturn(new FileInfos("testFile.xiidm", ByteArrayBuilder.NO_BYTES));
        mvc.perform(get("/" + VERSION + "/" + SWE_1D_UUID + "/" + processDate + "/export/XIIDM")
                .contentType(APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_OCTET_STREAM));

        UUID randomUuid = UUID.randomUUID();
        mergeConfigService.addConfig(new ProcessConfig(randomUuid, "FRESPT_2D", "2D", List.of("FR", "ES", "PT"), false, true, null, null));
        mvc.perform(get("/" + VERSION + "/" + randomUuid + "/" + processDate + "/export/CGMES")
                .contentType(APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_OCTET_STREAM));
    }

    @SneakyThrows
    private URI getReportServerURI(String uri) {
        return new URI(mergeConfigService.getReportServerURI() + uri);
    }

    @SneakyThrows
    @Test
    public void testReport() {
        mergeConfigService.addConfig(new ProcessConfig(FRES_2D_UUID, "FRES_2D", "2D", List.of("FR", "ES"), false, true, null, null));
        ZonedDateTime dateTime = ZonedDateTime.of(2019, 5, 1, 10, 0, 0, 0, ZoneId.of("UTC"));
        String mergeDate = DATE_FORMATTER.format(dateTime);

        // send tsos FR and ES with business process = 2D
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

        mockReportServer.expect(ExpectedCount.once(),
                requestTo(getReportServerURI(mergeRepository.findAll().get(0).getReportUUID().toString())))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));

        mockReportServer.expect(ExpectedCount.once(),
                requestTo(getReportServerURI(mergeRepository.findAll().get(0).getReportUUID().toString())))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK));

        mvc.perform(get(DELIMITER + VERSION + DELIMITER + FRES_2D_UUID + DELIMITER + URLEncoder.encode(mergeDate, StandardCharsets.UTF_8) + "/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));

        mvc.perform(delete(DELIMITER + VERSION + DELIMITER + FRES_2D_UUID + DELIMITER + URLEncoder.encode(mergeDate, StandardCharsets.UTF_8) + "/report"))
                .andExpect(status().isOk());
    }
}
