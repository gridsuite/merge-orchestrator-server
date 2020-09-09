package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.dto.CaseInfos;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class CaseInfosTest {

    @Test
    public void test() {
        UUID uuid = UUID.randomUUID();
        CaseInfos caseInfos = new CaseInfos("case", uuid, "XIIDM", "FR");
        assertEquals("XIIDM", caseInfos.getFormat());
        assertEquals("case", caseInfos.getName());
        assertEquals(uuid, caseInfos.getUuid());
        assertEquals("FR", caseInfos.getGeographicalCode());

        CaseInfos caseInfos2 = new CaseInfos();
        assertNull(caseInfos2.getFormat());
        assertNull(caseInfos2.getName());
        assertNull(caseInfos2.getUuid());
        assertNull(caseInfos2.getGeographicalCode());
    }
}
