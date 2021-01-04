/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class IgmQualityCheckServiceTest {

    private IgmQualityCheckService igmQualityCheckService;

    private UUID randomUuid1 = UUID.randomUUID();

    private UUID randomUuid2 = UUID.randomUUID();

    private UUID randomUuid3 = UUID.randomUUID();

    public static MockWebServer mockBackEnd;

    @AfterAll
    static void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @Before
    public void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
        String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
        igmQualityCheckService = new IgmQualityCheckService(WebClient.create(baseUrl));

        mockBackEnd.enqueue(new MockResponse()
                .setBody("{\"loadFlowOk\": \"true\"}")
                .addHeader("Content-Type", "application/json"));

        mockBackEnd.enqueue(new MockResponse()
                .setBody("{\"xxxxxxx\": \"true\"}")
                .addHeader("Content-Type", "application/json"));

        mockBackEnd.enqueue(new MockResponse()
                .setBody("{loadFlowOk\": \"true\"}")
                .addHeader("Content-Type", "application/json"));
    }

    @Test
    public void test() {

        StepVerifier.create(igmQualityCheckService.check(randomUuid1))
                .expectNext(true).verifyComplete();

        StepVerifier.create(igmQualityCheckService.check(randomUuid2))
                .expectNext(false).verifyComplete();

        StepVerifier.create(igmQualityCheckService.check(randomUuid3)).expectErrorSatisfies(e ->
                assertTrue(e.getMessage().contains("Error parsing case validation result")))
        .verify();
    }
}
