/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.apache.commons.io.FilenameUtils;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfos;
import org.gridsuite.merge.orchestrator.server.dto.FileInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
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
        networkConversionService = new NetworkConversionService(networkConversionServerRest, caseFetcherService, cgmesBoundaryService);
    }

    @Test
    public void testExportXiidm() {
        HttpHeaders header = new HttpHeaders();
        header.setContentDisposition(ContentDisposition.builder("attachment").filename("test_file.xiidm", StandardCharsets.UTF_8).build());
        when(networkConversionServerRest.exchange(anyString(),
                eq(HttpMethod.GET),
                any(),
                any(ParameterizedTypeReference.class),
                eq(networkUuid1.toString()),
                eq("XIIDM")))
                .thenReturn(new ResponseEntity(response, header, HttpStatus.OK));
        FileInfos res = networkConversionService.exportMerge(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), null, "XIIDM", "merge_name");
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

        when(cgmesBoundaryService.getLastBoundaries()).thenReturn(List.of(
                new BoundaryInfos("idTPBD", "TPBD", "TP content"),
                new BoundaryInfos("idEQBD", "EQBD", "EQ content")
        ));

        FileInfos res = networkConversionService.exportMerge(Arrays.asList(networkUuid1, networkUuid2, networkUuid3), new ArrayList<>(), "CGMES", "merge_name");

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
}
