/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import com.powsybl.cases.datasource.CaseDataSourceClient;
import com.powsybl.commons.PowsyblException;
import org.gridsuite.merge.orchestrator.server.dto.BoundaryInfos;
import org.gridsuite.merge.orchestrator.server.utils.CgmesUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class CgmesCaseDataSourceClient extends CaseDataSourceClient {
    private List<BoundaryInfos> boundaries;

    public CgmesCaseDataSourceClient(RestTemplate restTemplate, UUID caseUuid, List<BoundaryInfos> boundaries) {
        super(restTemplate, caseUuid);
        this.boundaries = boundaries;
    }

    @Override
    public InputStream newInputStream(String fileName) {
        if (!fileName.matches(CgmesUtils.EQBD_FILE_REGEX) && !fileName.matches(CgmesUtils.TPBD_FILE_REGEX)) {
            return super.newInputStream(fileName);
        } else {
            Optional<BoundaryInfos> replacingBoundary;
            if (fileName.matches(CgmesUtils.EQBD_FILE_REGEX)) {
                replacingBoundary = boundaries.stream().filter(b -> b.getFilename().matches(CgmesUtils.EQBD_FILE_REGEX)).findFirst();
            } else {
                replacingBoundary = boundaries.stream().filter(b -> b.getFilename().matches(CgmesUtils.TPBD_FILE_REGEX)).findFirst();
            }
            if (replacingBoundary.isPresent()) {
                return new ByteArrayInputStream(replacingBoundary.get().getBoundary().getBytes(StandardCharsets.UTF_8));
            } else {
                throw new PowsyblException("No replacing boundary available for replacement of boundary " + fileName + " !!");
            }
        }
    }
}
