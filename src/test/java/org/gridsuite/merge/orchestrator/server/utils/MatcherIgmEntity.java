/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.utils;

import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntity;
import org.gridsuite.merge.orchestrator.server.repositories.IgmEntityKey;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherIgmEntity extends TypeSafeMatcher<IgmEntity> {
    private final IgmEntity reference;

    public MatcherIgmEntity(UUID processUuid, LocalDateTime date, String tso, IgmStatus status, UUID networkUuid) {
        this.reference = new IgmEntity(new IgmEntityKey(processUuid, date, tso), status.name(), networkUuid, networkUuid, null, null, null, null);
    }

    @Override
    public boolean matchesSafely(IgmEntity m) {
        return reference.getKey().getProcessUuid().equals(m.getKey().getProcessUuid()) &&
                reference.getKey().getDate().equals(m.getKey().getDate()) &&
                reference.getKey().getTso().equals(m.getKey().getTso()) &&
                reference.getStatus().equals(m.getStatus()) &&
                reference.getNetworkUuid().equals(m.getNetworkUuid());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
