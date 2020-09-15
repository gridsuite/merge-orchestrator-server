/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
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
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@WebMvcTest(MergeOrchestratorController.class)
@ContextConfiguration(classes = {MergeOrchestratorApplication.class})
public class MergeOrchestratorControllerTest extends AbstractEmbeddedCassandraSetup {

    private static final UUID UUID_NETWORK = UUID.fromString("3f9985d2-94f5-4a5f-8e5a-ee218525d656");

    @Autowired
    private MockMvc mvc;

    @Inject
    MergeRepository mergeRepository;

    @Inject
    MergeIgmRepository mergeIgmRepository;

    @MockBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockBean
    private CaseFetcherService caseFetcherService;

    @MockBean
    private BalancesAdjustmentService balancesAdjustmentService;

    @MockBean
    private LoadFlowService loadFlowService;

    @Inject
    private MergeOrchestratorService mergeOrchestratorService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void configTest() throws Exception {
        mvc.perform(get("/" + VERSION + "/configs")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[{\"process\":\"SWE\",\"tsos\":[\"FR\",\"ES\",\"PT\"]}]"));
    }

    @Test
    public void test() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.of(2020, 7, 20, 10, 0, 0, 0, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("swe", dateTime.toLocalDateTime()), MergeStatus.LOADFLOW_SUCCEED.name()));
        mergeIgmRepository.insert(new MergeIgmEntity(new MergeEntityKey("swe", dateTime.toLocalDateTime()), "FR", IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String resExpected = "[{\"process\":\"swe\",\"date\":\"" + formatter.format(dateTime) + "\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}]";

        mvc.perform(get("/" + VERSION + "/swe/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        resExpected = "{\"process\":\"swe\",\"date\":\"" + formatter.format(dateTime) + "\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}";
        mvc.perform(get("/" + VERSION + "/swe/merges/" + URLEncoder.encode(formatter.format(dateTime), StandardCharsets.UTF_8))
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        mvc.perform(get("/" + VERSION + "/aaa/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }
}
