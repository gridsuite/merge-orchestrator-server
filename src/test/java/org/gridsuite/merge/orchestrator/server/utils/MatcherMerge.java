/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.utils;

import org.gridsuite.merge.orchestrator.server.dto.MergeStatus;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntity;
import org.gridsuite.merge.orchestrator.server.repositories.MergeEntityKey;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherMergeInfos<T extends MergeEntity> extends TypeSafeMatcher<T> {

    MergeEntity reference;

    public MatcherMergeInfos(String process, LocalDateTime date, MergeStatus status) {
        this.reference = new MergeEntity(new MergeEntityKey(process, date), status == null ? null : status.name());
    }

    @Override
    public boolean matchesSafely(T m) {
        return reference.getKey().getProcess().equals(m.getKey().getProcess()) &&
                reference.getKey().getDate().equals(m.getKey().getDate()) &&
                Objects.equals(reference.getStatus(), m.getStatus());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
