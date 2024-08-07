/*
  Copyright (c) 2021, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server.utils;

import com.powsybl.commons.report.ReportNode;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class MatcherReport extends TypeSafeMatcher<ReportNode> {

    ReportNode reference;

    public MatcherReport(ReportNode report) {
        this.reference = report;
    }

    @Override
    public boolean matchesSafely(ReportNode m) {
        return reference.getMessageKey().equals(m.getMessageKey()) &&
                reference.getMessage().equals(m.getMessage()) &&
                reference.getMessageTemplate().equals(m.getMessageTemplate());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(reference);
    }
}
