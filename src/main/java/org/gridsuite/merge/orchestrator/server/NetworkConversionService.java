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
    private static final String EQ_BD_FILE_NAME_SUFFIXE = "_EQ_BD.xml";
    private static final String TP_BD_FILE_NAME_SUFFIXE = "_TP_BD.xml";
    private static final String SV_PROFILE = "SV";
    private static final String UNDERSCORE = "_";
    private static final String FILE_VERSION = "1";
    private static final String XML_EXTENSION = ".xml";
    private static final String XML_ZIP = ".zip";
    private static final String SV_PROFILE_REGEX = "^(.*?(_SV_).*(.xml))$";

    private static final List<String> suffixeOfProfilesToSkip = new ArrayList<String>(Collections.singleton(CGMES_FORMAT));

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
            for (FileInfos mergedIgm : mergedIgms){
                cgmesProfiles.addAll(unzipCgmes(mergedIgm));
            }

            //Add SV profile
            cgmesProfiles.add(getSvProfile(networkUuids, baseFileName));

            //Add boundary files
            cgmesProfiles.addAll(getBoundaries());

            //Zip files
            ByteArrayOutputStream baosZip = createZipFile(cgmesProfiles);

            return new FileInfos(baseFileName.concat(UNDERSCORE + FILE_VERSION + XML_ZIP), baosZip.toByteArray());
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

    private List<FileInfos> getBoundaries() throws IOException {
        List<BoundaryInfos> boundariesInfos = cgmesBoundaryService.getBoundaries();
        List<FileInfos> boundaries = new ArrayList<>();
        for(BoundaryInfos boundaryInfos : boundariesInfos) {
            boundaries.add(new FileInfos(boundaryInfos.getFilename(), boundaryInfos.getBoundary().getBytes(StandardCharsets.UTF_8)));
        }
        return boundaries;
    }

    private List<FileInfos> unzipCgmes(FileInfos mergedIgm) throws IOException {
        List<FileInfos> profiles = new ArrayList<>();
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(mergedIgm.getNetworkData()));
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos;
        boolean isEntryToAdd;
        String fileName;
        try {
            ZipEntry entry = zis.getNextEntry();
            while(entry != null) {
                int length = -1;
                baos = new ByteArrayOutputStream();
                while ((length = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, length);
                }
                //Remove repertory name before file name
                fileName = FilenameUtils.getName(entry.getName());
                isEntryToAdd = !fileName.equals("") && !fileName.endsWith(EQ_BD_FILE_NAME_SUFFIXE) && !fileName.endsWith(TP_BD_FILE_NAME_SUFFIXE) && !fileName.matches(SV_PROFILE_REGEX);
                if(isEntryToAdd) {
                    profiles.add(new FileInfos(fileName, baos.toByteArray()));
                }
                entry = zis.getNextEntry();
                baos.close();
            }
        } finally {
            zis.close();
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

    public FileInfos getSvProfile(List<UUID> networksIds, String baseFileName) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export-sv-cgmes");
        for (int i = 1; i < networksIds.size(); ++i) {
            uriBuilder = uriBuilder.queryParam("networkUuid", networksIds.get(i).toString());
        }
        String uri = uriBuilder.build().toUriString();
        ResponseEntity<byte[]> responseEntity = networkConversionServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<byte[]>() { }, networksIds.get(0).toString(), CGMES_FORMAT);
        //TODO add file version (cim version ?)
        return new FileInfos(baseFileName.concat(UNDERSCORE + SV_PROFILE + UNDERSCORE + FILE_VERSION + XML_EXTENSION), responseEntity.getBody());
    }
}
