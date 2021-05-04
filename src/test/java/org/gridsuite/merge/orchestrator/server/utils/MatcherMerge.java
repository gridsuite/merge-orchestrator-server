/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.utils;

import org.gridsuite.merge.orchestrator.server.dto.Merge;
import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherMerge extends TypeSafeMatcher<Merge> {

    Merge reference;

    public MatcherMerge(UUID processUuid, ZonedDateTime date, MergeStatus status) {
        this.reference = new Merge(processUuid, date, status, List.of());
    }

    @Override
    public boolean matchesSafely(Merge m) {
        return reference.getProcessUuid().equals(m.getProcessUuid()) &&
                reference.getDate().equals(m.getDate()) &&
                reference.getStatus().equals(m.getStatus());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
