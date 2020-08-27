/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class CaseFetcherServiceTest {

    @Mock
    private RestTemplate caseServerRest;

    private CaseFetcherService caseFetcherService;

    private List<Map<String, String>> listCases;

    private UUID randomUuid1 = UUID.randomUUID();
    private UUID randomUuid2 = UUID.randomUUID();
    private UUID randomUuid3 = UUID.randomUUID();

    @Before
    public void setUp() {
        caseFetcherService = new CaseFetcherService(caseServerRest);

        listCases = new ArrayList<>();
        listCases.add(Map.of("name", "20200702_0030_2D1_FR1.zip", "uuid", randomUuid1.toString(), "format", "CGMES"));
        listCases.add(Map.of("name", "20200702_0030_2D1_ES1.zip", "uuid", randomUuid2.toString(), "format", "CGMES"));
        listCases.add(Map.of("name", "20200702_0030_2D1_PT1.zip", "uuid", randomUuid3.toString(), "format", "CGMES"));
    }

    @Test
    public void test() {
        when(caseServerRest.exchange(eq("/v1/cases/search?q=date:%222020-07-01T08:30:00Z%22%20AND%20geographicalCode:(DE)"),
                eq(HttpMethod.GET),
                eq(HttpEntity.EMPTY),
                eq(new ParameterizedTypeReference<List<Map<String, String>>>() { })))
                .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        List<CaseInfos> infos = caseFetcherService.getCases(asList("DE"), ZonedDateTime.parse("2020-07-01T10:30:00.000+02:00"));
        assertTrue(infos.isEmpty());

        when(caseServerRest.exchange(eq("/v1/cases/search?q=date:%222020-07-01T22:30:00Z%22%20AND%20geographicalCode:(FR%20OR%20ES%20OR%20PT)"),
                eq(HttpMethod.GET),
                eq(HttpEntity.EMPTY),
                eq(new ParameterizedTypeReference<List<Map<String, String>>>() { })))
                .thenReturn(ResponseEntity.ok(listCases));

        infos = caseFetcherService.getCases(asList("FR", "ES", "PT"), ZonedDateTime.parse("2020-07-02T00:30:00.000+02:00"));
        assertEquals(3, infos.size());
        assertEquals("20200702_0030_2D1_FR1.zip", infos.get(0).getName());
        assertEquals(randomUuid1.toString(), infos.get(0).getUuid().toString());
        assertEquals("CGMES", infos.get(0).getFormat());
        assertEquals("20200702_0030_2D1_ES1.zip", infos.get(1).getName());
        assertEquals(randomUuid2.toString(), infos.get(1).getUuid().toString());
        assertEquals("CGMES", infos.get(1).getFormat());
        assertEquals("20200702_0030_2D1_PT1.zip", infos.get(2).getName());
        assertEquals(randomUuid3.toString(), infos.get(2).getUuid().toString());
        assertEquals("CGMES", infos.get(2).getFormat());
    }
}
