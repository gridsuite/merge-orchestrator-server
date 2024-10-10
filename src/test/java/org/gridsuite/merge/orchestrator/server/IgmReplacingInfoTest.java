/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.IgmReplacingInfo;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
class IgmReplacingInfoTest {

    @Test
    void test() {
        UUID networkUuid = UUID.fromString("47b85a5c-44ec-4afc-9f7e-29e63368e83d");
        UUID caseUuid = UUID.fromString("4d6ac8c0-eaea-4b1c-8d28-a4297ad480b5");

        ZonedDateTime date = ZonedDateTime.now();
        IgmReplacingInfo igmInfo = new IgmReplacingInfo("FR", date, IgmStatus.VALIDATION_SUCCEED, caseUuid, networkUuid, "1D", null, null, null);
        assertEquals("FR", igmInfo.getTso());
        assertEquals(date, igmInfo.getDate());
        assertEquals(IgmStatus.VALIDATION_SUCCEED, igmInfo.getStatus());
        assertEquals(caseUuid, igmInfo.getCaseUuid());
        assertEquals(networkUuid, igmInfo.getNetworkUuid());
        assertEquals("1D", igmInfo.getBusinessProcess());
        assertNull(igmInfo.getOldNetworkUuid());

        IgmReplacingInfo igmInfo2 = new IgmReplacingInfo();
        assertNull(igmInfo2.getTso());
        assertNull(igmInfo2.getDate());
        assertNull(igmInfo2.getStatus());
        assertNull(igmInfo2.getNetworkUuid());
        assertNull(igmInfo2.getCaseUuid());
        assertNull(igmInfo2.getBusinessProcess());
        assertNull(igmInfo2.getOldNetworkUuid());
    }
}
