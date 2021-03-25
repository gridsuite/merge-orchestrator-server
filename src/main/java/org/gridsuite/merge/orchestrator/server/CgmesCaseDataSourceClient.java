/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import org.apache.commons.io.FilenameUtils;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfos;
import org.gridsuite.merge.orchestrator.server.utils.CgmesUtils;
import org.gridsuite.merge.orchestrator.server.utils.SecuredZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class CgmesCaseDataSourceClient extends CaseDataSourceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CgmesCaseDataSourceClient.class);

    private List<BoundaryInfos> boundaries;

    public CgmesCaseDataSourceClient(RestTemplate restTemplate, UUID caseUuid, List<BoundaryInfos> boundaries) {
        super(restTemplate, caseUuid);
        this.boundaries = boundaries;
    }

    private InputStream replaceBoundaries(InputStream input, String inputFileName) {
        boolean isEntryToAdd;
        String fileName;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream repackagedZip = new ZipOutputStream(baos);
             SecuredZipInputStream zis = new SecuredZipInputStream(input, CgmesUtils.MAX_ZIP_ENTRIES_COUNT, CgmesUtils.MAX_ZIP_SIZE)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (new File(entry.getName()).getCanonicalPath().contains("..")) {
                    throw new IllegalStateException("Entry is trying to leave the target dir: " + entry.getName());
                }

                // Remove repertory name before file name
                fileName = FilenameUtils.getName(entry.getName());

                // Check if it is a boundary file
                isEntryToAdd = !fileName.equals("") && !fileName.matches(CgmesUtils.EQBD_FILE_REGEX) && !fileName.matches(CgmesUtils.TPBD_FILE_REGEX);
                // If true, we don't add it to the result zip
                if (isEntryToAdd) {
                    repackagedZip.putNextEntry(new ZipEntry(fileName));
                    zis.transferTo(repackagedZip);
                    repackagedZip.closeEntry();
                }
                entry = zis.getNextEntry();
            }

            // Add last boundary files
            boundaries.forEach(b -> LOGGER.info("Using last boundary {} with uuid {} when importing {}", b.getFilename(), b.getId(), inputFileName));
            CgmesUtils.addFilesToZip(repackagedZip, CgmesBoundaryService.getFileInfosFromLastBoundaries(boundaries));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Override
    public InputStream newInputStream(String suffix, String ext) {
        InputStream inputStream = super.newInputStream(suffix, ext);
        return replaceBoundaries(inputStream, suffix + "." + ext);
    }

    @Override
    public InputStream newInputStream(String fileName) {
        InputStream inputStream = super.newInputStream(fileName);
        return replaceBoundaries(inputStream, fileName);
    }
}
