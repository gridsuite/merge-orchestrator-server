/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.BoundaryContent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class CgmesBoundaryServiceTest {
    @Mock
    private RestTemplate cgmesBoundaryServiceRest;

    private CgmesBoundaryService cgmesBoundaryService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        cgmesBoundaryService = new CgmesBoundaryService(cgmesBoundaryServiceRest);
    }

    @Test
    public void testLastBoundaries() {
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
    public void testSpecificBoundaries() {
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

