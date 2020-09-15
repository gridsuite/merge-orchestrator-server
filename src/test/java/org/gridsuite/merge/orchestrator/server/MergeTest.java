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
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
public class MergeTest {

    @Test
    public void test() {
        ZonedDateTime date = ZonedDateTime.now();
        Merge merge1 = new Merge("swe", date, MergeStatus.LOADFLOW_FAILED, Collections.singletonList(new Igm("FR", IgmStatus.AVAILABLE)));
        assertEquals("swe", merge1.getProcess());
        assertEquals(date, merge1.getDate());
        assertEquals(MergeStatus.LOADFLOW_FAILED, merge1.getStatus());
        assertEquals(1, merge1.getIgms().size());
        assertEquals("FR", merge1.getIgms().get(0).getTso());
        assertEquals(IgmStatus.AVAILABLE, merge1.getIgms().get(0).getStatus());

        Merge mergeinfos2 = new Merge();
        assertNull(mergeinfos2.getProcess());
        assertNull(mergeinfos2.getDate());
        assertNull(mergeinfos2.getStatus());
    }
}
