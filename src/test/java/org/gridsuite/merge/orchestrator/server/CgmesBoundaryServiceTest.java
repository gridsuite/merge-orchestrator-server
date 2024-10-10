/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.BoundaryContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class CgmesBoundaryServiceTest {
    @Mock
    private RestTemplate cgmesBoundaryServiceRest;

    private CgmesBoundaryService cgmesBoundaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        cgmesBoundaryService = new CgmesBoundaryService(cgmesBoundaryServiceRest);
    }

    @Test
    void testLastBoundaries() {
        List<Map<String, String>> response = new ArrayList<>(List.of(
            Map.of("id", "id1", "filename", "name1", "boundary", "boundary1"),
            Map.of("id", "id2", "filename", "name2", "boundary", "boundary2")
        ));

        when(cgmesBoundaryServiceRest.exchange(eq("/v1/boundaries/last"),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity(response, new HttpHeaders(), HttpStatus.OK));
        List<BoundaryContent> res = cgmesBoundaryService.getLastBoundaries();
        assertEquals(2, res.size());
        assertEquals("id1", res.get(0).getId());
        assertEquals("boundary1", res.get(0).getBoundary());
        assertEquals("name1", res.get(0).getFilename());

        assertEquals("id2", res.get(1).getId());
        assertEquals("boundary2", res.get(1).getBoundary());
        assertEquals("name2", res.get(1).getFilename());
    }

    @Test
    void testSpecificBoundaries() {
        Map<String, String> response = Map.of("id", "id1", "filename", "name1", "boundary", "boundary1");

        when(cgmesBoundaryServiceRest.exchange(eq("/v1/boundaries/id1"),
            eq(HttpMethod.GET),
            any(),
            any(ParameterizedTypeReference.class)))
            .thenReturn(new ResponseEntity(response, new HttpHeaders(), HttpStatus.OK));
        Optional<BoundaryContent> res = cgmesBoundaryService.getBoundary("id1");
        assertTrue(res.isPresent());
        assertEquals("id1", res.get().getId());
        assertEquals("boundary1", res.get().getBoundary());
        assertEquals("name1", res.get().getFilename());

        when(cgmesBoundaryServiceRest.exchange(eq("/v1/boundaries/id1"),
            eq(HttpMethod.GET),
            any(),
            any(ParameterizedTypeReference.class)))
            .thenReturn(new ResponseEntity(null, new HttpHeaders(), HttpStatus.OK));
        res = cgmesBoundaryService.getBoundary("id1");
        assertFalse(res.isPresent());
    }
}
