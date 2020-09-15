/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class BalancesAdjustmentServiceTest {

    @Mock
    private RestTemplate balancesAdjustmentServerRest;

    private BalancesAdjustmentService balancesAdjustmentService;

    private UUID randomUuid1 = UUID.randomUUID();
    private UUID randomUuid2 = UUID.randomUUID();
    private UUID randomUuid3 = UUID.randomUUID();

    @Before
    public void setUp() {
        balancesAdjustmentService = new BalancesAdjustmentService(balancesAdjustmentServerRest);
    }

    @Test
    public void test() {
        when(balancesAdjustmentServerRest.exchange(anyString(),
                eq(HttpMethod.PUT),
                any(),
                eq(String.class),
                eq(randomUuid1.toString())))
                .thenReturn(ResponseEntity.ok("{\"status\": \"SUCCESS\"}"));
        String res = balancesAdjustmentService.doBalance(Arrays.asList(randomUuid1, randomUuid2, randomUuid3));
        assertEquals("{\"status\": \"SUCCESS\"}", res);
    }
}
