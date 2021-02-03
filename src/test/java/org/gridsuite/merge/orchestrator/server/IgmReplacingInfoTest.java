/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.IgmReplacingInfo;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class IgmReplacingInfoTest {

    @Test
    public void test() {
        UUID networkUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();

        ZonedDateTime date = ZonedDateTime.now();
        IgmReplacingInfo igmInfo = new IgmReplacingInfo("FR", date, IgmStatus.VALIDATION_SUCCEED, caseUuid, networkUuid, "1D", null);
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
