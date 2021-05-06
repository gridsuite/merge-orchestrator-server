/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntityKey;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntityKey;
import org.junit.Test;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class EntityKeyTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.simple().forClass(MergeEntityKey.class).verify();
        EqualsVerifier.simple().forClass(IgmEntityKey.class).verify();
    }
}
