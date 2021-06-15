/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.merge.orchestrator.server.dto.ProcessConfig;
import org.gridsuite.merge.orchestrator.server.repositories.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = {MergeOrchestratorApplication.class})
public class ProcessConfigControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    ProcessConfigRepository processConfigRepository;

    @Autowired
    MergeRepository mergeRepository;

    @Autowired
    IgmRepository igmRepository;

    @MockBean
    private IgmQualityCheckService igmQualityCheckService;

    @MockBean
    private CaseFetcherService caseFetcherService;

    @MockBean
    private BalancesAdjustmentService balancesAdjustmentService;

    @MockBean
    private CgmesBoundaryService cgmesBoundaryService;

    @MockBean
    private LoadFlowService loadFlowService;

    @MockBean
    private NetworkConversionService networkConversionService;

    @MockBean
    private MergeEventService mergeEventService;

    private List<String> tsos = new ArrayList<>();

    private static final UUID SWE_1D_UUID = UUID.fromString("11111111-f60e-4766-bc5c-8f312c1984e4");

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        tsos.clear();
        tsos.add("FR");
        tsos.add("ES");
        tsos.add("PT");
        processConfigRepository.save(new ProcessConfigEntity(SWE_1D_UUID, "SWE_1D", "1D", tsos, false));
    }

    @Test
    public void configTest() throws Exception {
        mvc.perform(get("/" + VERSION + "/configs")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[{\"processUuid\":\"" + SWE_1D_UUID + "\",\"process\":\"SWE_1D\",\"businessProcess\":\"1D\",\"tsos\":[\"FR\",\"ES\",\"PT\"],\"runBalancesAdjustment\":false}]"));

        mvc.perform(get("/" + VERSION + "/configs/" + SWE_1D_UUID)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("{\"processUuid\":\"" + SWE_1D_UUID + "\",\"process\":\"SWE_1D\",\"businessProcess\":\"1D\",\"tsos\":[\"FR\",\"ES\",\"PT\"],\"runBalancesAdjustment\":false}"));

        mvc.perform(delete("/" + VERSION + "/configs/" + SWE_1D_UUID)
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk());

        mvc.perform(get("/" + VERSION + "/configs")
                .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));

        mvc.perform(post("/" + VERSION + "/configs")
                .contentType(APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(new ProcessConfig(SWE_1D_UUID, "SWE_1D", "1D", tsos, false))))
                .andExpect(status().isOk());

        UUID processUuid = processConfigRepository.findAll().get(0).getProcessUuid();

        mvc.perform(get("/" + VERSION + "/configs/" + processUuid)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(content().json("{\"processUuid\":\"" + processUuid + "\",\"process\":\"SWE_1D\",\"businessProcess\":\"1D\",\"tsos\":[\"FR\",\"ES\",\"PT\"],\"runBalancesAdjustment\":false}"));

        mvc.perform(delete("/" + VERSION + "/configs/" + processUuid)
            .contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}
