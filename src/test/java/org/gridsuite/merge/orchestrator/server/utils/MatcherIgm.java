/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.utils;

import org.gridsuite.merge.orchestrator.server.dto.Igm;
import org.gridsuite.merge.orchestrator.server.dto.IgmStatus;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Objects;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherIgm extends TypeSafeMatcher<Igm> {

    Igm reference;

    public MatcherIgm(String tso, IgmStatus status) {
        this.reference = new Igm(tso, status, null, null);
    }

    @Override
    public boolean matchesSafely(Igm m) {
        return reference.getTso().equals(m.getTso()) &&
                reference.getStatus().equals(m.getStatus()) &&
                Objects.equals(reference.getReplacingDate(), m.getReplacingDate()) &&
                Objects.equals(reference.getReplacingBusinessProcess(), m.getReplacingBusinessProcess());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
