/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import org.gridsuite.merge.orchestrator.server.dto.FileInfos;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import javax.inject.Inject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@WebMvcTest(MergeOrchestratorController.class)
@ContextConfiguration(classes = {MergeOrchestratorApplication.class})
public class MergeOrchestratorControllerTest extends AbstractEmbeddedCassandraSetup {

    private static final UUID UUID_NETWORK = UUID.randomUUID();
    private static final UUID UUID_NETWORK_MERGE_1 = UUID.randomUUID();
    private static final UUID UUID_NETWORK_MERGE_2 = UUID.randomUUID();
    private static final UUID UUID_NETWORK_MERGE_3 = UUID.randomUUID();
    private static final UUID UUID_CASE = UUID.randomUUID();
    private static final UUID UUID_CASE_MERGE_1 = UUID.randomUUID();
    private static final UUID UUID_CASE_MERGE_2 = UUID.randomUUID();
    private static final UUID UUID_CASE_MERGE_3 = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @Inject
    MergeRepository mergeRepository;

    @Inject
    IgmRepository igmRepository;

    @MockBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockBean
    private CaseFetcherService caseFetcherService;

    @MockBean
    private CgmesBoundaryService cgmesBoundaryService;

    @MockBean
    private BalancesAdjustmentService balancesAdjustmentService;

    @MockBean
    private LoadFlowService loadFlowService;

    @MockBean
    private NetworkConversionService networkConversionService;

    @MockBean
    private MergeOrchestratorConfigService mergeConfigService;

    @Inject
    private MergeOrchestratorService mergeOrchestratorService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.of(2020, 7, 20, 10, 0, 0, 0, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("SWE_1D", dateTime.toLocalDateTime()), MergeStatus.LOADFLOW_SUCCEED.name()));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_1D", dateTime.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK, UUID_CASE, null, null, null));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String resExpected = "[{\"process\":\"SWE_1D\",\"date\":\"" + formatter.format(dateTime) + "\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}]";

        mvc.perform(get("/" + VERSION + "/SWE_1D/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        mvc.perform(get("/" + VERSION + "/SWE_2D/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        mvc.perform(get("/" + VERSION + "/aaa_1D/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        String date = URLEncoder.encode(formatter.format(dateTime), StandardCharsets.UTF_8);
        mvc.perform(get("/" + VERSION + "/SWE_1D/merges?minDate=" + date + "&maxDate=" + date)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        ZonedDateTime dateTime2 = ZonedDateTime.of(2020, 7, 20, 10, 30, 0, 0, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("SWE_1D", dateTime2.toLocalDateTime()), MergeStatus.LOADFLOW_SUCCEED.name()));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_1D", dateTime2.toLocalDateTime(), "ES"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK, UUID_CASE, null, null, null));
        String resExpected2 = "[{\"process\":\"SWE_1D\",\"date\":\"2020-07-20T10:00:00Z\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}]";
        mvc.perform(get("/" + VERSION + "/SWE_1D/merges?minDate=" + date + "&maxDate=" + date)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected2));

        ZonedDateTime minDateTime = ZonedDateTime.of(2020, 7, 20, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime maxDateTime = ZonedDateTime.of(2020, 7, 20, 12, 0, 0, 0, ZoneId.of("UTC"));
        String minDate = URLEncoder.encode(formatter.format(minDateTime), StandardCharsets.UTF_8);
        String maxDate = URLEncoder.encode(formatter.format(maxDateTime), StandardCharsets.UTF_8);
        String resExpected3 = "[{\"process\":\"SWE_1D\",\"date\":\"2020-07-20T10:00:00Z\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]},{\"process\":\"SWE_1D\",\"date\":\"2020-07-20T10:30:00Z\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"ES\",\"status\":\"VALIDATION_SUCCEED\"}]}]";
        mvc.perform(get("/" + VERSION + "/SWE_1D/merges?minDate=" + minDate + "&maxDate=" + maxDate)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected3));

        ZonedDateTime dateTime3 = ZonedDateTime.of(2020, 7, 20, 10, 30, 0, 0, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("SWE_1D", dateTime3.toLocalDateTime()), MergeStatus.LOADFLOW_SUCCEED.name()));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_1D", dateTime3.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_MERGE_1, UUID_CASE_MERGE_1, null, null, null));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_1D", dateTime3.toLocalDateTime(), "ES"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_MERGE_2, UUID_CASE_MERGE_2, null, null, null));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("SWE_1D", dateTime3.toLocalDateTime(), "PT"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK_MERGE_3, UUID_CASE_MERGE_3, null, null, null));
        String processDate = URLEncoder.encode(formatter.format(dateTime3), StandardCharsets.UTF_8);
        given(networkConversionService.exportMerge(any(List.class), any(List.class), any(String.class), any(String.class)))
                .willReturn(new FileInfos("testFile.xiidm", ByteArrayBuilder.NO_BYTES));
        given(mergeConfigService.getConfig(any(String.class)))
                .willReturn(Optional.of(new ProcessConfig("SWE_1D", "1D", null, false)));
        mvc.perform(get("/" + VERSION + "/SWE_1D/" + processDate + "/export/XIIDM")
                .contentType(APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_OCTET_STREAM));

        mvc.perform(get("/" + VERSION + "/swe/" + processDate + "/export/CGMES")
                .contentType(APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_OCTET_STREAM));
    }
}
