/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.commons.PowsyblException;
import org.apache.commons.io.FilenameUtils;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryContent;
import org.gridsuite.merge.orchestrator.server.dto.FileInfos;
import org.gridsuite.merge.orchestrator.server.dto.NetworkInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(MockitoJUnitRunner.class)
public class NetworkConversionServiceTest {
    @Mock
    private RestTemplate networkConversionServerRest;

    private NetworkConversionService networkConversionService;

    @Mock
    private CaseFetcherService caseFetcherService;

    @Mock
    private CgmesBoundaryService cgmesBoundaryService;

    private UUID networkUuid1 = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
    private UUID networkUuid2 = UUID.fromString("da47a173-22d2-47e8-8a84-aa66e2d0fafb");
    private UUID networkUuid3 = UUID.fromString("4d6ac8c0-eaea-4b1c-8d28-a4297ad480b5");
    private byte[] response = "TestFileContent".getBytes();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        networkConversionService = new NetworkConversionService(networkConversionServerRest, caseFetcherService);
    }

    @Test
    public void testExportXiidm() {
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename("test_file.xiidm", StandardCharsets.UTF_8).build());
        when(networkConversionServerRest.exchange(anyString(),
                eq(HttpMethod.POST),
                any(),
                any(ParameterizedTypeReference.class),
                eq(networkUuid1.toString()),
                eq("XIIDM")))
                .thenReturn(new ResponseEntity(response, header, HttpStatus.OK));
        FileInfos res = networkConversionService.exportMerge(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), null, "XIIDM", "merge_name", Collections.emptyList());
        assertEquals(response, res.getData());
        assertEquals("merge_name.xiidm", res.getName());
    }

    @Test
    public void testExportCgmes() throws IOException, URISyntaxException {
        byte[] fileContentBe = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("MicroGridTestConfiguration_T4_BE_BB_Complete_v2.zip").toURI()));
        byte[] fileContentNl = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("MicroGridTestConfiguration_T4_NL_BB_Complete_v2.zip").toURI()));

        when(caseFetcherService.getCases(anyList())).thenReturn(List.of(
                new FileInfos("MicroGridBE", fileContentBe),
                new FileInfos("MicroGridNL", fileContentNl)
        ));

        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename("fileName", StandardCharsets.UTF_8).build());
        when(networkConversionServerRest.exchange(anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                eq(networkUuid1.toString()),
                eq("CGMES")))
                .thenReturn(new ResponseEntity("SV content".getBytes(), header, HttpStatus.OK));

        List<BoundaryContent> boundaries = List.of(new BoundaryContent("idEQBD", "EQBD", "EQ content"), new BoundaryContent("idTPBD", "TPBD", "TP content"));

        FileInfos res = networkConversionService.exportMerge(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), new ArrayList<>(), "CGMES", "merge_name", boundaries);

        Map<String, byte[]> files = new HashMap<>();
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos;
        String fileName;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(res.getData()))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                int length = -1;
                long totalBytes = 0;
                baos = new ByteArrayOutputStream();
                while ((length = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, length);
                    totalBytes = totalBytes + length;
                }
                fileName = FilenameUtils.getName(entry.getName());
                files.put(fileName, baos.toByteArray());
                entry = zis.getNextEntry();
                baos.close();
            }
        }

        assertEquals("merge_name_001.zip", res.getName());
        assertEquals(15, files.size());
        assertEquals("EQ content", new String(files.get("EQBD"), StandardCharsets.UTF_8));
        assertEquals("TP content", new String(files.get("TPBD"), StandardCharsets.UTF_8));
        assertEquals("SV content", new String(files.get("merge_name_SV_001.xml"), StandardCharsets.UTF_8));

        assertTrue(files.containsKey("20171002T0930Z_NL_DY_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_NL_DL_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_1D_NL_SSH_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_1D_NL_TP_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_NL_GL_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_NL_EQ_6.xml"));

        assertTrue(files.containsKey("20171002T0930Z_BE_DY_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_BE_DL_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_1D_BE_SSH_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_1D_BE_TP_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_BE_GL_6.xml"));
        assertTrue(files.containsKey("20171002T0930Z_BE_EQ_6.xml"));
    }

    @Test
    public void importCaseTest() {
        List<BoundaryContent> boundaries = new ArrayList<>();
        String boundary1Id = "idBoundary1";
        String boundary1Filename = "boundary1.xml";
        String boundary1Content = "fake content of boundary1";
        String boundary2Id = "idBoundary2";
        String boundary2Filename = "boundary2.xml";
        String boundary2Content = "fake content of boundary2";
        boundaries.add(new BoundaryContent(boundary1Id, boundary1Filename, boundary1Content));
        boundaries.add(new BoundaryContent(boundary2Id, boundary2Filename, boundary2Content));

        UUID caseUuid = UUID.fromString("b3a4bbc6-567d-48d4-a05d-5a109213c524");
        UUID networkUuid = UUID.fromString("44a1954e-96b5-4be1-81c4-2a5b48e6a558");
        String networkId = "networkId";

        ArgumentMatcher<HttpEntity<List<BoundaryContent>>> matcher = r -> {
            List<BoundaryContent> requestBoundaries = r.getBody();
            return requestBoundaries.get(0).getId().equals(boundary1Id) &&
                requestBoundaries.get(0).getFilename().equals(boundary1Filename) &&
                requestBoundaries.get(0).getBoundary().equals(boundary1Content) &&
                requestBoundaries.get(1).getId().equals(boundary2Id) &&
                requestBoundaries.get(1).getFilename().equals(boundary2Filename) &&
                requestBoundaries.get(1).getBoundary().equals(boundary2Content);
        };

        when(networkConversionServerRest.exchange(anyString(),
            eq(HttpMethod.POST),
            argThat(matcher),
            eq(NetworkInfos.class)
        )).thenReturn(ResponseEntity.ok(new NetworkInfos(networkUuid, networkId)));
        assertEquals(networkUuid, networkConversionService.importCase(caseUuid, boundaries));

        // errors tests
        when(networkConversionServerRest.exchange(anyString(),
            eq(HttpMethod.POST),
            argThat(matcher),
            eq(NetworkInfos.class)
        )).thenReturn(ResponseEntity.ok(null));
        assertThrows(PowsyblException.class, () -> networkConversionService.importCase(caseUuid, boundaries));

        when(networkConversionServerRest.exchange(anyString(),
            eq(HttpMethod.POST),
            argThat(matcher),
            eq(NetworkInfos.class)
        )).thenThrow(RestClientException.class);
        assertThrows(PowsyblException.class, () -> networkConversionService.importCase(caseUuid, boundaries));
    }
}
