/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.gridsuite.merge.orchestrator.server.dto.FileInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
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

    private UUID caseUuid1 = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
    private UUID caseUuid2 = UUID.fromString("da47a173-22d2-47e8-8a84-aa66e2d0fafb");
    private UUID caseUuid3 = UUID.fromString("4d6ac8c0-eaea-4b1c-8d28-a4297ad480b5");

    @Mock
    private NetworkStoreService networkStoreService;

    @Before
    public void setUp() {
        caseFetcherService = new CaseFetcherService(caseServerRest, networkStoreService);

        listCases = new ArrayList<>();
        listCases.add(Map.of("name", "20200702_0030_2D1_FR1.zip", "uuid", caseUuid1.toString(), "format", "CGMES", "tso", "FR", "businessProcess", "1D"));
        listCases.add(Map.of("name", "20200702_0030_2D1_ES1.zip", "uuid", caseUuid2.toString(), "format", "CGMES", "tso", "ES", "businessProcess", "1D"));
        listCases.add(Map.of("name", "20200702_0030_2D1_PT1.zip", "uuid", caseUuid3.toString(), "format", "CGMES", "tso", "PT", "businessProcess", "1D"));
    }

    @Test
    public void test() {
        when(caseServerRest.exchange(eq("/v1/cases/search?q={q}"),
            eq(HttpMethod.GET),
            eq(HttpEntity.EMPTY),
            eq(new ParameterizedTypeReference<List<Map<String, String>>>() {
            }),
            eq("date:\"2020-07-01T10:30:00Z\" AND tso:(DE) AND format:CGMES AND businessProcess:1D")))
            .thenReturn(ResponseEntity.ok(Collections.emptyList()));

        List<CaseInfos> infos = caseFetcherService.getCases(asList("DE"), ZonedDateTime.parse("2020-07-01T10:30:00.000+02:00"), "CGMES", "1D");
        assertTrue(infos.isEmpty());

        when(caseServerRest.exchange(eq("/v1/cases/search?q={q}"),
            eq(HttpMethod.GET),
            eq(HttpEntity.EMPTY),
            eq(new ParameterizedTypeReference<List<Map<String, String>>>() {
            }),
            eq("date:\"2020-07-02T00:30:00Z\" AND tso:(FR OR ES OR PT) AND format:CGMES AND businessProcess:1D")))
            .thenReturn(ResponseEntity.ok(listCases));

        infos = caseFetcherService.getCases(asList("FR", "ES", "PT"), ZonedDateTime.parse("2020-07-02T00:30:00.000+02:00"), "CGMES", "1D");
        assertEquals(3, infos.size());
        assertEquals("20200702_0030_2D1_FR1.zip", infos.get(0).getName());
        assertEquals(caseUuid1.toString(), infos.get(0).getUuid().toString());
        assertEquals("CGMES", infos.get(0).getFormat());
        assertEquals("FR", infos.get(0).getTso());
        assertEquals("1D", infos.get(0).getBusinessProcess());

        assertEquals("20200702_0030_2D1_ES1.zip", infos.get(1).getName());
        assertEquals(caseUuid2.toString(), infos.get(1).getUuid().toString());
        assertEquals("CGMES", infos.get(1).getFormat());
        assertEquals("ES", infos.get(1).getTso());
        assertEquals("1D", infos.get(1).getBusinessProcess());

        assertEquals("20200702_0030_2D1_PT1.zip", infos.get(2).getName());
        assertEquals(caseUuid3.toString(), infos.get(2).getUuid().toString());
        assertEquals("CGMES", infos.get(2).getFormat());
        assertEquals("PT", infos.get(2).getTso());
        assertEquals("1D", infos.get(2).getBusinessProcess());

        List<UUID> caseUuids = List.of(caseUuid1, caseUuid2);
        HttpHeaders header = new HttpHeaders();
        when(caseServerRest.exchange(anyString(),
            eq(HttpMethod.GET),
            eq(HttpEntity.EMPTY),
            eq(new ParameterizedTypeReference<byte[]>() {
            }),
            anyString()))
            .thenReturn(new ResponseEntity("fileContent".getBytes(), header, HttpStatus.OK));

        List<FileInfos> fileInfos = caseFetcherService.getCases(caseUuids);
        assertEquals(2, fileInfos.size());
        assertEquals(caseUuid1.toString(), fileInfos.get(0).getName());
        assertEquals("fileContent", new String(fileInfos.get(0).getData(), StandardCharsets.UTF_8));
        assertEquals(caseUuid2.toString(), fileInfos.get(1).getName());
        assertEquals("fileContent", new String(fileInfos.get(1).getData(), StandardCharsets.UTF_8));
    }
}
