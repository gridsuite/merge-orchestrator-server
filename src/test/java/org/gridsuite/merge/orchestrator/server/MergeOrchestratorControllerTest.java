/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

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
import org.springframework.test.web.servlet.MvcResult;

import javax.inject.Inject;

import java.net.URLEncoder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static junit.framework.TestCase.assertEquals;
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

    @Autowired
    private MockMvc mvc;

    @Inject
    MergeRepository mergeRepository;

    @Inject
    IgmQualityRepository igmQualityRepository;

    @Inject
    ParametersRepository parametersRepository;

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
        ArrayList<String> tsos = new ArrayList<>();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        parametersRepository.save(new ParametersEntity("SWE", tsos, false));
    }

    private UUID uuid = UUID.randomUUID();

    private static final UUID UUID_CASE = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID UUID_NETWORK = UUID.fromString("3f9985d2-94f5-4a5f-8e5a-ee218525d656");

    @Test
    public void configTest() throws Exception {
        mvc.perform(get("/" + VERSION + "/configs")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[{\"process\":\"SWE\",\"tsos\":[\"FR\",\"ES\",\"PT\"]}]"));

        mvc.perform(get("/" + VERSION + "/configs/SWE")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("{\"process\":\"SWE\",\"tsos\":[\"FR\",\"ES\",\"PT\"]}"));
    }

    @Test
    public void test() throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.of(2020, 07, 20, 10, 00, 00, 00, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("swe", dateTime.toLocalDateTime()), "TSO_IGM", uuid));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String resExpected = "[{\"process\":\"swe\",\"date\":\"" + formatter.format(dateTime) + "\",\"status\":\"TSO_IGM\"}]";

        MvcResult result = mvc.perform(get("/" + VERSION + "/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertEquals(resExpected, result.getResponse().getContentAsString());

        result = mvc.perform(get("/" + VERSION + "/merges/swe")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertEquals(resExpected, result.getResponse().getContentAsString());

        resExpected = "{\"process\":\"swe\",\"date\":\"" + formatter.format(dateTime) + "\",\"status\":\"MERGE_STARTED\"}";
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("swe", dateTime.toLocalDateTime()), "MERGE_STARTED", null));
        result = mvc.perform(get("/" + VERSION + "/merges/swe/" + URLEncoder.encode(formatter.format(dateTime), "UTF-8"))
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertEquals(resExpected, result.getResponse().getContentAsString());

        resExpected = "{\"caseUuid\":\"7928181c-7977-4592-ba19-88027e4254e4\",\"networkId\":\"3f9985d2-94f5-4a5f-8e5a-ee218525d656\",\"valid\":true}";
        igmQualityRepository.insert(new IgmQualityEntity(UUID_CASE, UUID_NETWORK, dateTime.toLocalDateTime(), true));
        result = mvc.perform(get("/" + VERSION + "/merges/" + UUID_CASE.toString() + "/quality")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn();
        assertEquals(resExpected, result.getResponse().getContentAsString());
    }
}
