/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertTrue;
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

    private void testZipContent(InputStream input) {
        Set<String> expectedEntries = Set.of("20171002T0930Z_1D_BE_SSH_6.xml", "20171002T0930Z_1D_BE_SV_6.xml",
            "20171002T0930Z_1D_BE_TP_6.xml", "20171002T0930Z_BE_DL_6.xml", "20171002T0930Z_BE_DY_6.xml",
            "20171002T0930Z_BE_EQ_6.xml", "20171002T0930Z_BE_GL_6.xml", "boundary1.xml", "boundary2.xml");
        try (ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                assertTrue(expectedEntries.contains(FilenameUtils.getName(entry.getName())));
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void test() throws IOException, URISyntaxException {
        UUID caseUuid = UUID.randomUUID();
        List<BoundaryInfos> boundaries = new ArrayList<>();
        boundaries.add(new BoundaryInfos("urn:uuid:f1582c44-d9e2-4ea0-afdc-dba189ab4358", "boundary1.xml", "fake content of boundary1"));
        boundaries.add(new BoundaryInfos("urn:uuid:3e3f7738-aab9-4284-a965-71d5cd151f71", "boundary2.xml", "fake content of boundary2"));

        String fileName = "MicroGridTestConfiguration_T4_BE_BB_Complete_v2.zip";
        byte[] initialZip = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource(fileName).toURI()));

        CgmesCaseDataSourceClient client = new CgmesCaseDataSourceClient(caseServerRest, caseUuid, boundaries);

        // test with filename
        given(caseServerRest.exchange(eq("/v1/cases/" + caseUuid + "/datasource?fileName=" + fileName),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)))
            .willReturn(ResponseEntity.ok(initialZip));

        InputStream input = client.newInputStream(fileName);
        testZipContent(input);

        // test with suffixe and extension
        String suffix = StringUtils.removeEnd(fileName, ".zip");
        String ext = "zip";
        given(caseServerRest.exchange(eq("/v1/cases/" + caseUuid + "/datasource?suffix=" + suffix + "&ext=" + ext),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)))
            .willReturn(ResponseEntity.ok(initialZip));

        input = client.newInputStream(suffix, ext);
        testZipContent(input);
    }
}
