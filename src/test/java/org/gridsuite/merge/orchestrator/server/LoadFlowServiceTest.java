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
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
                                              ArgumentMatcher<LoadFlowParameters> paramsMatcher) {
        when(loadFlowServerRest.exchange(anyString(),
            eq(HttpMethod.PUT),
            any(),
            eq(LoadFlowResult.class),
            eq(networkUuid.toString()),
            argThat(paramsMatcher)))
            .thenReturn(ResponseEntity.ok(new LoadFlowResultImpl(true, Collections.emptyMap(), null, componentResults)));
    }

    @Test
    public void test() {
        List<LoadFlowResult.ComponentResult> componentResultsOk = Collections.singletonList(new LoadFlowResultImpl.ComponentResultImpl(0, LoadFlowResult.ComponentResult.Status.CONVERGED, 5, "slackBusId", 0));
        List<LoadFlowResult.ComponentResult> componentResultsNok = Collections.singletonList(new LoadFlowResultImpl.ComponentResultImpl(0, LoadFlowResult.ComponentResult.Status.FAILED, 20, "slackBusId", 0));

        // first loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsOk, params -> params.isTransformerVoltageControlOn() && params.isSimulShunt());

        MergeStatus status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3));
        assertEquals(MergeStatus.FIRST_LOADFLOW_SUCCEED, status);

        // first loadflow fails, but second loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params -> params.isTransformerVoltageControlOn() && params.isSimulShunt());
        addLoadFlowResultExpectation(networkUuid1, componentResultsOk, params -> !params.isTransformerVoltageControlOn() && !params.isSimulShunt());

        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3));
        assertEquals(MergeStatus.SECOND_LOADFLOW_SUCCEED, status);

        // first loadflow fails, second loadflow fails, but third loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params -> params.isTransformerVoltageControlOn() && params.isSimulShunt());
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params -> !params.isTransformerVoltageControlOn() && !params.isSimulShunt());
        addLoadFlowResultExpectation(networkUuid1, componentResultsOk, params -> params.isNoGeneratorReactiveLimits());

        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3));
        assertEquals(MergeStatus.THIRD_LOADFLOW_SUCCEED, status);

        // neither loadflow succeeds
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params -> params.isTransformerVoltageControlOn() && params.isSimulShunt());
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params -> !params.isTransformerVoltageControlOn() && !params.isSimulShunt());
        addLoadFlowResultExpectation(networkUuid1, componentResultsNok, params -> !params.isNoGeneratorReactiveLimits());

        status = loadFlowService.run(Arrays.asList(networkUuid1, networkUuid2, networkUuid3));
        assertEquals(MergeStatus.LOADFLOW_FAILED, status);
    }
}
