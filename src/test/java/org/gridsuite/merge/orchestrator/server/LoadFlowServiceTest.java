/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class LoadFlowServiceTest {

    public static MockWebServer mockBackEnd;

    @Autowired
    private LoadFlowService loadFlowService;

    private UUID randomUuid1 = UUID.randomUUID();
    private UUID randomUuid2 = UUID.randomUUID();
    private UUID randomUuid3 = UUID.randomUUID();

    @Before
    public void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
        String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
        loadFlowService = new LoadFlowService(baseUrl);
    }

    @After
    public void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @Test
    public void test() {
        mockBackEnd.enqueue(new MockResponse()
                .setBody("{\"status\": \"TRUE\"}")
                .addHeader("Content-Type", "application/json"));

        StepVerifier.create(loadFlowService.run(Arrays.asList(randomUuid1, randomUuid2, randomUuid3)))
                .expectNextMatches(response -> response.equals("{\"status\": \"TRUE\"}"))
                .verifyComplete();
    }
}
