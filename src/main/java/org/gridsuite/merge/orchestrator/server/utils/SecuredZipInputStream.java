package org.gridsuite.merge.orchestrator.server.utils;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
public class SecuredZipInputStream extends ZipInputStream {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final int maxZipEntries;
    private final long maxSize;
    private int entryCount = 0;
    private int totalReadBytes = 0;

    public SecuredZipInputStream(InputStream in, int maxZipEntries, long maxSize) {
        this(in, StandardCharsets.UTF_8, maxZipEntries, maxSize);
    }

    public SecuredZipInputStream(InputStream in, Charset charset, int maxZipEntries, long maxSize) {
        super(in, charset);
        this.maxZipEntries = maxZipEntries;
        this.maxSize = maxSize;
    }

    @Override
    public ZipEntry getNextEntry() {
        if (++entryCount > maxZipEntries) {
            throw new IllegalStateException("Zip has too many entries.");
        }
        try {
            return super.getNextEntry();
        } catch  (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len + totalReadBytes > maxSize) {
            throw new IllegalStateException("Zip size is too big.");
        }

        int readBytes = super.read(b, off, len);

        totalReadBytes += readBytes;
        if (totalReadBytes > maxSize) {
            throw new IllegalStateException("Zip size is too big.");
        }

        return readBytes;
    }
}
