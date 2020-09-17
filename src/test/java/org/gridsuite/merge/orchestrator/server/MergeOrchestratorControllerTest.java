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
import java.util.ArrayList;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
    IgmRepository igmRepository;

    @Inject
    ProcessConfigRepository processConfigRepository;

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
        processConfigRepository.save(new ProcessConfigEntity("SWE", tsos, false));
    }

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
        ZonedDateTime dateTime = ZonedDateTime.of(2020, 7, 20, 10, 0, 0, 0, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("swe", dateTime.toLocalDateTime()), MergeStatus.LOADFLOW_SUCCEED.name()));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("swe", dateTime.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String resExpected = "[{\"process\":\"swe\",\"date\":\"" + formatter.format(dateTime) + "\",\"status\":\"LOADFLOW_SUCCEED\",\"igms\":[{\"tso\":\"FR\",\"status\":\"VALIDATION_SUCCEED\"}]}]";

        mvc.perform(get("/" + VERSION + "/swe/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        mvc.perform(get("/" + VERSION + "/aaa/merges")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        String date = URLEncoder.encode(formatter.format(dateTime), StandardCharsets.UTF_8);
        mvc.perform(get("/" + VERSION + "/swe/merges?minDate=" + date + "&maxDate=" + date)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(resExpected));

        ZonedDateTime dateTime2 = ZonedDateTime.of(2020, 7, 20, 10, 30, 0, 0, ZoneId.of("UTC"));
        mergeRepository.insert(new MergeEntity(new MergeEntityKey("swe", dateTime2.toLocalDateTime()), MergeStatus.LOADFLOW_SUCCEED.name()));
        igmRepository.insert(new IgmEntity(new IgmEntityKey("swe", dateTime2.toLocalDateTime(), "FR"), IgmStatus.VALIDATION_SUCCEED.name(), UUID_NETWORK));
        mvc.perform(get("/" + VERSION + "/swe/merges?minDate=" + date + "&maxDate=" + date)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)));

        ZonedDateTime minDateTime = ZonedDateTime.of(2020, 7, 20, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime maxDateTime = ZonedDateTime.of(2020, 7, 20, 12, 0, 0, 0, ZoneId.of("UTC"));
        String minDate = URLEncoder.encode(formatter.format(minDateTime), StandardCharsets.UTF_8);
        String maxDate = URLEncoder.encode(formatter.format(maxDateTime), StandardCharsets.UTF_8);
        mvc.perform(get("/" + VERSION + "/swe/merges?minDate=" + minDate + "&maxDate=" + maxDate)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
