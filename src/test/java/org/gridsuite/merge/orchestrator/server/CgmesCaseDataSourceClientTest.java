/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.apache.commons.io.IOUtils;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class CgmesCaseDataSourceClientTest {

    @Mock
    private RestTemplate caseServerRest;

    @Before
    public void setUp() {
    }

    @Test
    public void test() throws IOException, URISyntaxException {
        UUID caseUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        List<BoundaryInfos> boundaries = new ArrayList<>();
        String eqbdContent = "fake content of eqbd boundary";
        String tpbdContent = "fake content of tpbd boundary";

        boundaries.add(new BoundaryInfos("urn:uuid:f1582c44-d9e2-4ea0-afdc-dba189ab4358", "20201121T0000Z__ENTSOE_EQBD_003.xml", eqbdContent));
        boundaries.add(new BoundaryInfos("urn:uuid:3e3f7738-aab9-4284-a965-71d5cd151f71", "20201205T1000Z__ENTSOE_TPBD_004.xml", tpbdContent));

        byte[] sshContent = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("20210326T0930Z_1D_BE_SSH_6.xml").toURI()));

        CgmesCaseDataSourceClient client = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, boundaries);

        given(caseServerRest.exchange(eq("/v1/cases/" + caseUuid + "/datasource?fileName=20210326T0930Z_1D_BE_SSH_6.xml"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)))
            .willReturn(ResponseEntity.ok(sshContent));

        InputStream input = client.newInputStream("20210326T0930Z_1D_BE_SSH_6.xml");
        assertArrayEquals(sshContent, IOUtils.toByteArray(input));

        input = client.newInputStream("20210326T0000Z__ENTSOE_EQBD_101.xml");
        assertArrayEquals(eqbdContent.getBytes(StandardCharsets.UTF_8), IOUtils.toByteArray(input));

        input = client.newInputStream("20210326T0000Z__ENTSOE_TPBD_6.xml");
        assertArrayEquals(tpbdContent.getBytes(StandardCharsets.UTF_8), IOUtils.toByteArray(input));
    }
}
