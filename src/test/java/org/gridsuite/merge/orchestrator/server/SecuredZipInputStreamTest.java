package org.gridsuite.merge.orchestrator.server;

import org.gridsuite.merge.orchestrator.server.utils.SecuredZipInputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SecuredZipInputStreamTest {
    @Test
    public void test() throws URISyntaxException, IOException {
        byte[] fileContent = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource("MicroGridTestConfiguration_T4_BE_BB_Complete_v2.zip").toURI()));
        ByteArrayInputStream bais = new ByteArrayInputStream(fileContent);
        SecuredZipInputStream tooManyEntriesSecuredZis = new SecuredZipInputStream(new ByteArrayInputStream(fileContent), 3, 1000000000);
        assertThrows(IllegalStateException.class, () -> readZip(tooManyEntriesSecuredZis))
                    .getMessage().contains("Zip has too many entries.");

        SecuredZipInputStream tooBigSecuredZis = new SecuredZipInputStream(new ByteArrayInputStream(fileContent), 1000, 15000);
        assertThrows(IllegalStateException.class, () -> readZip(tooBigSecuredZis))
                .getMessage().contains("Zip size is too big.");

        SecuredZipInputStream okSecuredZis = new SecuredZipInputStream(new ByteArrayInputStream(fileContent), 1000, 1000000000);
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileContent));
        assertEquals(readZip(zis), readZip(okSecuredZis));

    }

    public int readZip(ZipInputStream zis) throws IOException {
        ZipEntry entry = zis.getNextEntry();
        int readBytes = 0;
        while (entry != null) {
            readBytes += zis.readAllBytes().length;
            entry = zis.getNextEntry();
        }
        return  readBytes;
    }
}
