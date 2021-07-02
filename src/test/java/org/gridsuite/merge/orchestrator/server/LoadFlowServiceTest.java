/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class LoadFlowServiceTest {

    @Mock
    private RestTemplate loadFlowServerRest;

    private LoadFlowService loadFlowService;

    private UUID networkUuid1 = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
    private UUID networkUuid2 = UUID.fromString("da47a173-22d2-47e8-8a84-aa66e2d0fafb");
    private UUID networkUuid3 = UUID.fromString("4d6ac8c0-eaea-4b1c-8d28-a4297ad480b5");

    @Before
    public void setUp() {
        loadFlowService = new LoadFlowService(loadFlowServerRest);
    }

    private void addLoadFlowResultExpectation(UUID networkUuid,
                                              List<LoadFlowResult.ComponentResult> componentResults,
                                              LoadFlowParameters params) {
        ArgumentMatcher<HttpEntity<byte[]>> matcher = r -> JsonLoadFlowParameters.read(new ByteArrayInputStream(r.getBody())).toString().equals(params.toString());

        when(loadFlowServerRest.exchange(anyString(),
            eq(HttpMethod.PUT),
            argThat(matcher),
            eq(LoadFlowResult.class),
            eq(networkUuid.toString())))
            .thenReturn(ResponseEntity.ok(componentResults != null ?
                new LoadFlowResultImpl(true, Collections.emptyMap(), null, componentResults)
                : null));
    }

    @Test
    public void test() {
        List<LoadFlowResult.ComponentResult> componentResultsOk = Collections.singletonList(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "slackBusId", 0));
        List<LoadFlowResult.ComponentResult> componentResultsNok = Collections.singletonList(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.FAILED, 20, "slackBusId", 0));
        List<LoadFlowResult.ComponentResult> componentResultsEmpty = Collections.emptyList();

        UUID reportId = UUID.randomUUID();
        // first loadflow succeeds
        LoadFlowParameters params1 = new LoadFlowParameters()
            .setTransformerVoltageControlOn(true)
            .setSimulShunt(true)
            .setDistributedSlack(true)
            .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
            .setReadSlackBus(true)
            .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        addLoadFlowResultExpectation(networkUuid1, componentResultsOk, params1);

        MergeStatus status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), reportId);
        assertEquals(MergeStatus.FIRST_LOADFLOW_SUCCEED, status);

        // first loadflow fails, but second loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params1);

        LoadFlowParameters params2 = new LoadFlowParameters()
            .setTransformerVoltageControlOn(false)
            .setSimulShunt(false)
            .setDistributedSlack(true)
            .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
            .setReadSlackBus(true)
            .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        addLoadFlowResultExpectation(networkUuid1, componentResultsOk, params2);

        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), reportId);
        assertEquals(MergeStatus.SECOND_LOADFLOW_SUCCEED, status);

        // first loadflow fails, second loadflow fails, but third loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params1);
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params2);

        LoadFlowParameters params3 = new LoadFlowParameters()
            .setTransformerVoltageControlOn(false)
            .setSimulShunt(false)
            .setDistributedSlack(true)
            .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
            .setReadSlackBus(true)
            .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES)
            .setNoGeneratorReactiveLimits(true);
        addLoadFlowResultExpectation(networkUuid1, componentResultsOk, params3);

        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), reportId);
        assertEquals(MergeStatus.THIRD_LOADFLOW_SUCCEED, status);

        // neither loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params1);
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params2);
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params3);

        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), reportId);
        assertEquals(MergeStatus.LOADFLOW_FAILED, status);

        // test with empty componentResults and null LoadFlowResult
        addLoadFlowResultExpectation(networkUuid1, componentResultsEmpty, params1);
        addLoadFlowResultExpectation(networkUuid1, componentResultsEmpty, params2);
        addLoadFlowResultExpectation(networkUuid1, componentResultsEmpty, params3);
        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), reportId);
        assertEquals(MergeStatus.LOADFLOW_FAILED, status);

        addLoadFlowResultExpectation(networkUuid1, null, params1);
        addLoadFlowResultExpectation(networkUuid1, null, params2);
        addLoadFlowResultExpectation(networkUuid1, null, params3);
        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), reportId);
        assertEquals(MergeStatus.LOADFLOW_FAILED, status);
    }
}
