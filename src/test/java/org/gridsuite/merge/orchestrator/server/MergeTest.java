/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.Igm;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.dto.Merge;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
class MergeTest {

    private static final UUID SWE_1D_UUID = UUID.fromString("11111111-f60e-4766-bc5c-8f312c1984e4");

    @Test
    void test() {
        ZonedDateTime date = ZonedDateTime.now();
        Merge merge1 = new Merge(SWE_1D_UUID, date, MergeStatus.LOADFLOW_FAILED, Collections.singletonList(new Igm("FR", IgmStatus.AVAILABLE, null, null)));
        assertEquals(SWE_1D_UUID, merge1.getProcessUuid());
        assertEquals(date, merge1.getDate());
        assertEquals(MergeStatus.LOADFLOW_FAILED, merge1.getStatus());
        assertEquals(1, merge1.getIgms().size());
        assertEquals("FR", merge1.getIgms().get(0).getTso());
        assertEquals(IgmStatus.AVAILABLE, merge1.getIgms().get(0).getStatus());
        assertNull(merge1.getIgms().get(0).getReplacingDate());
        assertNull(merge1.getIgms().get(0).getReplacingBusinessProcess());

        Merge mergeinfos2 = new Merge();
        assertNull(mergeinfos2.getProcessUuid());
        assertNull(mergeinfos2.getDate());
        assertNull(mergeinfos2.getStatus());
        assertNull(mergeinfos2.getIgms());
    }
}
