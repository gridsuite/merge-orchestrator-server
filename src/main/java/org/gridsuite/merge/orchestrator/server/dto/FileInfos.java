/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@AllArgsConstructor
@Getter
public class FileInfos {

    private String networkName;

    private byte[] networkData;

}
