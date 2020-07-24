/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.PowsyblException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private UUID randomUuid1 = UUID.randomUUID();
    private UUID randomUuid2 = UUID.randomUUID();

    @Before
    public void setUp() {
        loadFlowService = new LoadFlowService(loadFlowServerRest);
    }

    @Test
    public void test() {
        when(loadFlowServerRest.exchange(anyString(),
                eq(HttpMethod.PUT),
                any(),
                eq(String.class),
                eq(randomUuid1.toString())))
                .thenReturn(ResponseEntity.ok("{\"status\": \"TRUE\"}"));
        Boolean res = loadFlowService.run(randomUuid1);
        assertEquals(Boolean.TRUE, res);

        when(loadFlowServerRest.exchange(anyString(),
                eq(HttpMethod.PUT),
                any(),
                eq(String.class),
                eq(randomUuid2.toString())))
                .thenReturn(ResponseEntity.ok("{\"status\": \"FALSE\"}"));
        res = loadFlowService.run(randomUuid2);
        assertEquals(Boolean.FALSE, res);

        when(loadFlowServerRest.exchange(anyString(),
                eq(HttpMethod.PUT),
                any(),
                eq(String.class),
                eq(randomUuid2.toString())))
                .thenReturn(ResponseEntity.ok("{statut: \"FALSE\"}"));

        assertTrue(assertThrows(PowsyblException.class, () -> loadFlowService.run(randomUuid2)).getMessage().contains("Error parsing loadflow result"));

        when(loadFlowServerRest.exchange(anyString(),
                eq(HttpMethod.PUT),
                any(),
                eq(String.class),
                eq(randomUuid2.toString())))
                .thenReturn(ResponseEntity.ok("{\"statut\": \"FALSE\"}"));
        assertNull(loadFlowService.run(randomUuid2));
    }
}
