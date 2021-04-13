/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.utils;

import org.gridsuite.merge.orchestrator.server.dto.FileInfos;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
public final class CgmesUtils {
    public static final int MAX_ZIP_ENTRIES_COUNT = 100;
    public static final int MAX_ZIP_SIZE = 1000000000;
    public static final String TPBD_FILE_REGEX = "^(.*?(__ENTSOE_TPBD_).*(.xml))$";
    public static final String EQBD_FILE_REGEX = "^(.*?(__ENTSOE_EQBD_).*(.xml))$";

    private CgmesUtils() {
    }

    public static void addFilesToZip(ZipOutputStream zos, List<FileInfos> files) throws IOException {
        ZipEntry entry;
        for (FileInfos file : files) {
            entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);
            zos.write(file.getData());
            zos.closeEntry();
        }
    }
}
