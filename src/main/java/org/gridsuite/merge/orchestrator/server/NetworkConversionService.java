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
import org.gridsuite.merge.orchestrator.server.utils.CgmesUtils;
import org.gridsuite.merge.orchestrator.server.utils.SecuredZipInputStream;
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
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
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
    private static final String FILE_VERSION = "001";
    private static final String XML_EXTENSION = ".xml";
    private static final String ZIP = ".zip";
    private static final String SV_PROFILE_REGEX = "^(.*?(_SV_).*(.xml))$";

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

    public NetworkConversionService(RestTemplate networkConversionServerRest, CaseFetcherService caseFetcherService, CgmesBoundaryService cgmesBoundaryService) {
        this.networkConversionServerRest = networkConversionServerRest;
        this.caseFetcherService = caseFetcherService;
        this.cgmesBoundaryService = cgmesBoundaryService;
    }

    public FileInfos exportMerge(List<UUID> networkUuids, List<UUID> caseUuids, String format, String baseFileName) {
        if (format.equals(CGMES_FORMAT)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try (ZipOutputStream repackagedZip = new ZipOutputStream(baos)) {
                //Add merged IGMs profiles
                List<FileInfos> mergedIgms = caseFetcherService.getCases(caseUuids);
                for (FileInfos mergedIgm : mergedIgms) {
                    addFilteredCgmesFiles(repackagedZip, mergedIgm.getData());
                }

                //Add SV profile
                CgmesUtils.addFilesToZip(repackagedZip, Collections.singletonList(getSvProfile(networkUuids, baseFileName)));

                //Add boundary files
                CgmesUtils.addFilesToZip(repackagedZip, getBoundaries());

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new FileInfos(baseFileName.concat(UNDERSCORE + FILE_VERSION + ZIP), baos.toByteArray());
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

    private void addFilteredCgmesFiles(ZipOutputStream repackagedZip, byte[] cgmesZip) {
        boolean isEntryToAdd;
        String fileName;
        try (SecuredZipInputStream zis = new SecuredZipInputStream(new ByteArrayInputStream(cgmesZip), CgmesUtils.MAX_ZIP_ENTRIES_COUNT, CgmesUtils.MAX_ZIP_SIZE)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (new File(entry.getName()).getCanonicalPath().contains("..")) {
                    throw new IllegalStateException("Entry is trying to leave the target dir: " + entry.getName());
                }

                //Remove repertory name before file name
                fileName = FilenameUtils.getName(entry.getName());

                //Check if it is a boundary file or SV profile
                isEntryToAdd = !fileName.equals("") && !fileName.matches(CgmesUtils.EQBD_FILE_REGEX) && !fileName.matches(CgmesUtils.TPBD_FILE_REGEX) && !fileName.matches(SV_PROFILE_REGEX);
                //If true, we don't add it to the result zip
                if (isEntryToAdd) {
                    repackagedZip.putNextEntry(new ZipEntry(fileName));
                    zis.transferTo(repackagedZip);
                    repackagedZip.closeEntry();
                }
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileInfos getSvProfile(List<UUID> networksIds, String baseFileName) {
        String uri = DELIMITER + NETWORK_CONVERSION_API_VERSION + "/networks/{networkUuid}/export-sv-cgmes?";
        uri += networksIds.stream().skip(1).map(s -> "networkUuid=" + s.toString()).collect(Collectors.joining("&"));
        ResponseEntity<byte[]> responseEntity = networkConversionServerRest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<byte[]>() { }, networksIds.get(0).toString(), CGMES_FORMAT);
        return new FileInfos(baseFileName.concat(UNDERSCORE + SV_PROFILE + UNDERSCORE + FILE_VERSION + XML_EXTENSION), responseEntity.getBody());
    }
}
