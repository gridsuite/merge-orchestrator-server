/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
class ReplacingDateTest {

    @Test
    void test() {
        ReplacingDate rDate = new ReplacingDate("2021-01-14T09:30:00Z", "1D");
        assertEquals("2021-01-14T09:30:00Z", rDate.getDate());
        assertEquals("1D", rDate.getBusinessProcess());

        rDate = new ReplacingDate();
        assertNull(rDate.getDate());
        assertNull(rDate.getBusinessProcess());
    }
}
