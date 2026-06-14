package com.cloudvault.file;

import java.io.InputStream;

public record FileDownload(
        String filename,
        String contentType,
        long contentLength,
        InputStream content
) {
}
