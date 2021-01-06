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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@Service
public class NetworkConversionService {
    private static final String NETWORK_CONVERSION_API_VERSION = "v1";
    private static final String DELIMITER = "/";
    private static final String CGMES_FORMAT = "CGMES";
    private static final String SV_PROFILE = "SV";
    private static final String UNDERSCORE = "_";
    private static final String FILE_VERSION = "1";
    private static final String XML_EXTENSION = ".xml";
    private static final String ZIP = ".zip";
    private static final String SV_PROFILE_REGEX = "^(.*?(_SV_).*(.xml))$";
    private static final String TPBD_FILE_REGEX = "^(.*?(__ENTSOE_TPBD_).*(.xml))$";
    private static final String EQBD_FILE_REGEX = "^(.*?(__ENTSOE_EQBD_).*(.xml))$";
    private static final int MAX_ZIP_ENTRIES_NUMBER = 100;
    private static final int MAX_ZIP_ENTRY_SIZE = 1000000000;

    private RestTemplate networkConversionServerRest;

    private CaseFetcherService caseFetcherService;

    private CgmesBoundaryService cgmesBoundaryService;

    @Autowired
    public NetworkConversionService(CaseFetcherService caseFetcherService, CgmesBoundaryService cgmesBoundaryService, RestTemplateBuilder builder,
                                    @Value("${backing-services.network-conversion.base-uri:http://network-conversion-server/}") String networkConversionBaseUri) {
        this.caseFetcherService = caseFetcherService;
        this.cgmesBoundaryService = cgmesBoundaryService;
        this.networkConversionServerRest = builder.uriTemplateHandler(
                new DefaultUriBuilderFactory(networkConversionBaseUri)
        ).build();
    }

    public NetworkConversionService(RestTemplate restTemplate) {
        this.networkConversionServerRest = restTemplate;
    }

    public FileInfos exportMerge(List<UUID> networkUuids, List<UUID> caseUuids, String format, String baseFileName) throws IOException {
        if (format.equals(CGMES_FORMAT)) {

            List<FileInfos> cgmesProfiles = new ArrayList<>();

            //Add merged IGMs profiles
            List<FileInfos> mergedIgms = caseFetcherService.getCases(caseUuids);
            for (FileInfos mergedIgm : mergedIgms) {
                cgmesProfiles.addAll(unzipCgmes(mergedIgm));
            }

            //Add SV profile
            cgmesProfiles.add(getSvProfile(networkUuids, baseFileName));

            //Add boundary files
            cgmesProfiles.addAll(getBoundaries());

            //Zip files
            ByteArrayOutputStream baosZip = createZipFile(cgmesProfiles);

            return new FileInfos(baseFileName.concat(UNDERSCORE + FILE_VERSION + ZIP), baosZip.toByteArray());
        } else {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export/{format}");
            for (int i = 1; i < networkUuids.size(); ++i) {
                uriBuilder = uriBuilder.queryParam("networkUuid", networkUuids.get(i).toString());
            }
            String uri = uriBuilder.build().toUriString();
            ResponseEntity<byte[]> responseEntity = networkConversionServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<byte[]>() { }, networkUuids.get(0).toString(), format);
            String exportedFileExtension;
            try {
                String exportedFileName = responseEntity.getHeaders().getContentDisposition().getFilename();
                exportedFileExtension = Objects.nonNull(exportedFileName) ? exportedFileName.substring(exportedFileName.lastIndexOf(".")) : ".unknown";
            } catch (IndexOutOfBoundsException e) {
                exportedFileExtension = ".unknown";
            }
            return new FileInfos(baseFileName.concat(exportedFileExtension), responseEntity.getBody());
        }
    }

    private List<FileInfos> getBoundaries() {
        List<BoundaryInfos> boundariesInfos = cgmesBoundaryService.getBoundaries();
        List<FileInfos> boundaries = new ArrayList<>();
        for (BoundaryInfos boundaryInfos : boundariesInfos) {
            boundaries.add(new FileInfos(boundaryInfos.getFilename(), boundaryInfos.getBoundary().getBytes(StandardCharsets.UTF_8)));
        }
        return boundaries;
    }

    private List<FileInfos> unzipCgmes(FileInfos mergedIgm) throws IOException {
        List<FileInfos> profiles = new ArrayList<>();
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos;
        boolean isEntryToAdd;
        String fileName;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(mergedIgm.getNetworkData()))) {
            ZipEntry entry = zis.getNextEntry();
            entryCount++;
            if (entryCount > MAX_ZIP_ENTRIES_NUMBER) {
                throw new IllegalStateException("Zip has too many entries.");
            }
            while (entry != null) {
                if (new File(entry.getName()).getCanonicalPath().contains("..")) {
                    throw new IllegalStateException("Entry is trying to leave the target dir: " + entry.getName());
                }

                int length = -1;
                long totalBytes = 0;
                baos = new ByteArrayOutputStream();
                while (totalBytes < MAX_ZIP_ENTRY_SIZE && (length = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, length);
                    totalBytes = totalBytes + length;
                }
                //Remove repertory name before file name
                fileName = FilenameUtils.getName(entry.getName());
                isEntryToAdd = !fileName.equals("") && !fileName.matches(EQBD_FILE_REGEX) && !fileName.matches(TPBD_FILE_REGEX) && !fileName.matches(SV_PROFILE_REGEX);
                if (isEntryToAdd) {
                    profiles.add(new FileInfos(fileName, baos.toByteArray()));
                }
                entry = zis.getNextEntry();
                baos.close();
            }
        }
        return profiles;
    }

    private static ByteArrayOutputStream createZipFile(List<FileInfos> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry;
            for (FileInfos file : files) {
                entry = new ZipEntry(file.getNetworkName());
                zos.putNextEntry(entry);
                zos.write(file.getNetworkData());
                zos.closeEntry();
            }
        }
        return baos;
    }

    private FileInfos getSvProfile(List<UUID> networksIds, String baseFileName) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export-sv-cgmes");
        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        String uri = uriBuilder.build().toUriString();
        ResponseEntity<byte[]> responseEntity = networkConversionServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<byte[]>() { }, networksIds.get(0).toString(), CGMES_FORMAT);
        return new FileInfos(baseFileName.concat(UNDERSCORE + SV_PROFILE + UNDERSCORE + FILE_VERSION + XML_EXTENSION), responseEntity.getBody());
    }
}
