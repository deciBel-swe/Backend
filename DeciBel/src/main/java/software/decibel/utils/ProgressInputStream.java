package software.decibel.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProgressInputStream extends FilterInputStream {
    private final long totalBytes;
    private final ProgressCallback callback;
    private long bytesRead = 0;

    public ProgressInputStream(InputStream in, long totalBytes, ProgressCallback callback) {
        super(in);
        this.totalBytes = totalBytes;
        this.callback = callback;
    }

    @Override
    public int read() throws IOException {
        int n = super.read();
        if (n != -1) {
            bytesRead++;
            if (callback != null) {
                callback.onProgress(bytesRead, totalBytes);
            }
        }
        return n;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        // Limit max read size to 8KB per call to ensure frequent progress updates for small files
        int maxLen = Math.min(len, 8 * 1024);
        int n = super.read(b, off, maxLen);
        if (n != -1) {
            bytesRead += n;
            if (callback != null) {
                callback.onProgress(bytesRead, totalBytes);
            }
        }
        return n;
    }
}
