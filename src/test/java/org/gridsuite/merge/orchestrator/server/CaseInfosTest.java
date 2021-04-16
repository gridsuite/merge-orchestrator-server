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
        UUID uuid = UUID.fromString("0e14e487-5182-470a-ba33-859a9d4a5061");
        CaseInfos caseInfos = new CaseInfos("case", uuid, "XIIDM", "FR", "1D");
        assertEquals("XIIDM", caseInfos.getFormat());
        assertEquals("case", caseInfos.getName());
        assertEquals(uuid, caseInfos.getUuid());
        assertEquals("FR", caseInfos.getTso());
        assertEquals("1D", caseInfos.getBusinessProcess());

        CaseInfos caseInfos2 = new CaseInfos();
        assertNull(caseInfos2.getFormat());
        assertNull(caseInfos2.getName());
        assertNull(caseInfos2.getUuid());
        assertNull(caseInfos2.getTso());
        assertNull(caseInfos2.getBusinessProcess());
    }
}
