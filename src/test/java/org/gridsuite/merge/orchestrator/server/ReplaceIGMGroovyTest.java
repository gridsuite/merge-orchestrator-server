/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.merge.orchestrator.server;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class ReplaceIGMGroovyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceIGMGroovyTest.class);

    GroovyShell shell;
    Script replacingIGMScript;

    @Before
    public void setUp() {
        shell = new GroovyShell();
        try {
            replacingIGMScript = shell.parse(new InputStreamReader(new ClassPathResource("replaceIGM.groovy").getInputStream()));
        } catch (Exception exc) {
            LOGGER.error(exc.getMessage());
        }
    }

    @Test
    public void test() {
        Assert.assertEquals("[date:2021-01-11T02:30:00Z businessProcess:1D, date:2021-01-11T04:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T01:30:00Z businessProcess:1D, date:2021-01-11T05:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T00:30:00Z businessProcess:1D, date:2021-01-08T03:30:00Z businessProcess:1D, " +
                        "date:2021-01-08T02:30:00Z businessProcess:1D, date:2021-01-08T04:30:00Z businessProcess:1D, " +
                        "date:2021-01-08T01:30:00Z businessProcess:1D, date:2021-01-08T05:30:00Z businessProcess:1D, " +
                        "date:2021-01-08T00:30:00Z businessProcess:1D, date:2021-01-11T06:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T07:30:00Z businessProcess:1D, date:2021-01-11T08:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T09:30:00Z businessProcess:1D, date:2021-01-11T10:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T11:30:00Z businessProcess:1D, date:2021-01-11T12:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T13:30:00Z businessProcess:1D, date:2021-01-11T14:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T15:30:00Z businessProcess:1D, date:2021-01-11T16:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T17:30:00Z businessProcess:1D, date:2021-01-11T18:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T19:30:00Z businessProcess:1D, date:2021-01-11T20:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T21:30:00Z businessProcess:1D, date:2021-01-11T22:30:00Z businessProcess:1D, " +
                        "date:2021-01-11T23:30:00Z businessProcess:1D]",
                MergeOrchestratorService.execReplaceGroovyScript(replacingIGMScript, "2021-01-11T03:30:00Z", "SWE_1D", "1D").toString());

        Assert.assertEquals("[date:2021-01-14T10:30:00Z businessProcess:2D, date:2021-01-14T08:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T11:30:00Z businessProcess:2D, date:2021-01-14T12:30:00Z businessProcess:2D, " +
                        "date:2021-01-13T09:30:00Z businessProcess:2D, date:2021-01-13T10:30:00Z businessProcess:2D, " +
                        "date:2021-01-13T08:30:00Z businessProcess:2D, date:2021-01-13T11:30:00Z businessProcess:2D, " +
                        "date:2021-01-13T12:30:00Z businessProcess:2D, date:2021-01-14T00:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T01:30:00Z businessProcess:2D, date:2021-01-14T02:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T03:30:00Z businessProcess:2D, date:2021-01-14T04:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T05:30:00Z businessProcess:2D, date:2021-01-14T06:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T07:30:00Z businessProcess:2D, date:2021-01-14T13:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T14:30:00Z businessProcess:2D, date:2021-01-14T15:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T16:30:00Z businessProcess:2D, date:2021-01-14T17:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T18:30:00Z businessProcess:2D, date:2021-01-14T19:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T20:30:00Z businessProcess:2D, date:2021-01-14T21:30:00Z businessProcess:2D, " +
                        "date:2021-01-14T22:30:00Z businessProcess:2D, date:2021-01-14T23:30:00Z businessProcess:2D]",
                MergeOrchestratorService.execReplaceGroovyScript(replacingIGMScript, "2021-01-14T09:30:00Z", "SWE_2D", "2D").toString());

        Assert.assertEquals("[date:2021-01-16T20:30:00Z businessProcess:SN, date:2021-01-16T22:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T19:30:00Z businessProcess:SN, date:2021-01-16T23:30:00Z businessProcess:SN, " +
                        "date:2021-01-09T21:30:00Z businessProcess:SN, date:2021-01-09T20:30:00Z businessProcess:SN, " +
                        "date:2021-01-09T22:30:00Z businessProcess:SN, date:2021-01-09T19:30:00Z businessProcess:SN, " +
                        "date:2021-01-09T23:30:00Z businessProcess:SN, date:2021-01-16T00:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T01:30:00Z businessProcess:SN, date:2021-01-16T02:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T03:30:00Z businessProcess:SN, date:2021-01-16T04:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T05:30:00Z businessProcess:SN, date:2021-01-16T06:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T07:30:00Z businessProcess:SN, date:2021-01-16T08:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T09:30:00Z businessProcess:SN, date:2021-01-16T10:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T11:30:00Z businessProcess:SN, date:2021-01-16T12:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T13:30:00Z businessProcess:SN, date:2021-01-16T14:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T15:30:00Z businessProcess:SN, date:2021-01-16T16:30:00Z businessProcess:SN, " +
                        "date:2021-01-16T17:30:00Z businessProcess:SN, date:2021-01-16T18:30:00Z businessProcess:SN]",
                MergeOrchestratorService.execReplaceGroovyScript(replacingIGMScript, "2021-01-16T21:30:00Z", "SWE_SN", "SN").toString());

        Assert.assertEquals("[date:2021-01-17T16:30:00Z businessProcess:2D, date:2021-01-17T17:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T14:30:00Z businessProcess:2D, date:2021-01-17T13:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T12:30:00Z businessProcess:2D, date:2021-01-10T15:30:00Z businessProcess:2D, " +
                        "date:2021-01-10T16:30:00Z businessProcess:2D, date:2021-01-10T17:30:00Z businessProcess:2D, " +
                        "date:2021-01-10T14:30:00Z businessProcess:2D, date:2021-01-10T13:30:00Z businessProcess:2D, " +
                        "date:2021-01-10T12:30:00Z businessProcess:2D, date:2021-01-17T00:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T01:30:00Z businessProcess:2D, date:2021-01-17T02:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T03:30:00Z businessProcess:2D, date:2021-01-17T04:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T05:30:00Z businessProcess:2D, date:2021-01-17T06:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T07:30:00Z businessProcess:2D, date:2021-01-17T08:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T09:30:00Z businessProcess:2D, date:2021-01-17T10:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T11:30:00Z businessProcess:2D, date:2021-01-17T18:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T19:30:00Z businessProcess:2D, date:2021-01-17T20:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T21:30:00Z businessProcess:2D, date:2021-01-17T22:30:00Z businessProcess:2D, " +
                        "date:2021-01-17T23:30:00Z businessProcess:2D]",
                MergeOrchestratorService.execReplaceGroovyScript(replacingIGMScript, "2021-01-17T15:30:00Z", "SWE_2D", "2D").toString());
    }
}
