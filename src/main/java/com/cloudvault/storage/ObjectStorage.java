package com.cloudvault.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorage {

    void upload(String objectKey, InputStream content, long contentLength, String contentType);

    InputStream download(String objectKey);

    PresignedStorageUrl createUploadUrl(
            String objectKey,
            String contentType,
            Duration expiration
    );

    PresignedStorageUrl createDownloadUrl(
            String objectKey,
            String filename,
            Duration expiration
    );

    Optional<StoredObjectMetadata> stat(String objectKey);

    void delete(String objectKey);
}
