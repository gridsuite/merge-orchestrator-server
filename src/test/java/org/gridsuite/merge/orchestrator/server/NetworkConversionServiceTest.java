/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.ExportNetworkInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class NetworkConversionServiceTest {
    @Mock
    private RestTemplate networkConversionServerRest;

    private NetworkConversionService networkConversionService;

    private UUID randomUuid1 = UUID.randomUUID();
    private UUID randomUuid2 = UUID.randomUUID();
    private UUID randomUuid3 = UUID.randomUUID();
    private byte[] response = "TestFileContent".getBytes();

    @Before
    public void setUp() {
        networkConversionService = new NetworkConversionService(networkConversionServerRest);
    }

    @Test
    public void test() {
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename("test_file.xiidm", StandardCharsets.UTF_8).build());
        when(networkConversionServerRest.exchange(anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                eq(randomUuid1.toString()),
                eq("XIIDM")))
                .thenReturn(new ResponseEntity(response, header, HttpStatus.OK));
        ExportNetworkInfos res = networkConversionService.exportMerge(Arrays.asList(randomUuid1, randomUuid2, randomUuid3), "XIIDM", "merge_name");
        assertEquals(response, res.getNetworkData());
        assertEquals("merge_name.xiidm", res.getNetworkName());
    }
}