package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.MergeInfos;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MergeInfosTest {

    @Test
    public void test() {
        LocalDateTime date = LocalDateTime.now();
        MergeInfos mergeInfos1 = new MergeInfos("swe", date, "MERGE_STARTED");
        assertEquals("swe", mergeInfos1.getProcess());
        assertEquals(date, mergeInfos1.getDate());
        assertEquals("MERGE_STARTED", mergeInfos1.getStatus());

        MergeInfos mergeinfos2 = new MergeInfos();
        assertNull(mergeinfos2.getProcess());
        assertNull(mergeinfos2.getDate());
        assertNull(mergeinfos2.getStatus());
    }
}
