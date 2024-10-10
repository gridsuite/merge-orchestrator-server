/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.IgmReplacingInfo;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@WebMvcTest(MergeOrchestratorController.class)
class MergeOrchestratorControllerReplaceIGMTest {
    @Autowired
    private MockMvc mvc;

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
    private MergeOrchestratorConfigService mergeOrchestratorConfigService;

    @MockBean
    private MergeEventService mergeEventService;

    @MockBean
    private MergeOrchestratorService mergeOrchestratorService;

    @MockBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testReplaceIGM() throws Exception {
        UUID uuidCaseIdFr = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID uuidNetworkIdFr = UUID.fromString("8928181c-7977-4592-ba19-88027e4254e4");
        UUID uuidCaseIdEs = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID uuidNetworkIdEs = UUID.fromString("8928181c-7977-4592-ba19-88027e4254e5");

        ZonedDateTime dateTime = ZonedDateTime.of(2020, 7, 20, 10, 0, 0, 0, ZoneId.of("UTC"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String processDate = URLEncoder.encode(formatter.format(dateTime), StandardCharsets.UTF_8);

        Map<String, IgmReplacingInfo> expectedInfos = new HashMap<>();
        expectedInfos.put("FR", new IgmReplacingInfo("FR", dateTime, IgmStatus.VALIDATION_SUCCEED, uuidCaseIdFr, uuidNetworkIdFr, "2D", null, null, null));
        expectedInfos.put("ES", new IgmReplacingInfo("ES", dateTime, IgmStatus.VALIDATION_SUCCEED, uuidCaseIdEs, uuidNetworkIdEs, "2D", null, null, null));

        given(mergeOrchestratorService.replaceIGMs(any(UUID.class), any(ZonedDateTime.class)))
                .willReturn(new HashMap<>(expectedInfos));

        MvcResult result = mvc.perform(put("/" + VERSION + "/" + UUID.randomUUID() + "/" + processDate + "/replace-igms")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();

        assertEquals("{\"FR\":{\"tso\":\"FR\",\"date\":\"2020-07-20T10:00:00Z\",\"status\":\"VALIDATION_SUCCEED\",\"caseUuid\":\"7928181c-7977-4592-ba19-88027e4254e4\",\"networkUuid\":\"8928181c-7977-4592-ba19-88027e4254e4\",\"businessProcess\":\"2D\",\"oldNetworkUuid\":null,\"eqBoundary\":null,\"tpBoundary\":null},\"ES\":{\"tso\":\"ES\",\"date\":\"2020-07-20T10:00:00Z\",\"status\":\"VALIDATION_SUCCEED\",\"caseUuid\":\"7928181c-7977-4592-ba19-88027e4254e5\",\"networkUuid\":\"8928181c-7977-4592-ba19-88027e4254e5\",\"businessProcess\":\"2D\",\"oldNetworkUuid\":null,\"eqBoundary\":null,\"tpBoundary\":null}}",
                result.getResponse().getContentAsString());
    }
}
