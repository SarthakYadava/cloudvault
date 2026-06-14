package com.cloudvault.storage;

import com.cloudvault.config.CloudVaultProperties;
import com.cloudvault.error.StorageOperationException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ContentDisposition;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3ObjectStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            CloudVaultProperties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = properties.aws().bucket();
    }

    @Override
    public PresignedStorageUrl createUploadUrl(
            String objectKey,
            String contentType,
            Duration expiration
    ) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(objectRequest)
                .build();

        try {
            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(request);
            return new PresignedStorageUrl(
                    presigned.url().toExternalForm(),
                    presigned.httpRequest().method().name(),
                    Map.of("Content-Type", contentType),
                    Instant.now().plus(expiration)
            );
        } catch (SdkClientException exception) {
            throw new StorageOperationException(
                    "The upload URL could not be created.",
                    exception
            );
        }
    }

    @Override
    public PresignedStorageUrl createDownloadUrl(
            String objectKey,
            String filename,
            Duration expiration
    ) {
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseContentDisposition(disposition)
                .build();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(objectRequest)
                .build();

        try {
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(request);
            return new PresignedStorageUrl(
                    presigned.url().toExternalForm(),
                    presigned.httpRequest().method().name(),
                    Map.of(),
                    Instant.now().plus(expiration)
            );
        } catch (SdkClientException exception) {
            throw new StorageOperationException(
                    "The download URL could not be created.",
                    exception
            );
        }
    }

    @Override
    public Optional<StoredObjectMetadata> stat(String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            HeadObjectResponse response = s3Client.headObject(request);
            return Optional.of(new StoredObjectMetadata(
                    response.contentLength(),
                    response.contentType()
            ));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageOperationException(
                    "The uploaded file could not be verified.",
                    exception
            );
        } catch (SdkClientException exception) {
            throw new StorageOperationException(
                    "The uploaded file could not be verified.",
                    exception
            );
        }
    }

    @Override
    public void upload(String objectKey, InputStream content, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
        } catch (S3Exception | SdkClientException exception) {
            throw new StorageOperationException("The file could not be uploaded to object storage.", exception);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            return response;
        } catch (S3Exception | SdkClientException exception) {
            throw new StorageOperationException("The file could not be read from object storage.", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (S3Exception | SdkClientException exception) {
            throw new StorageOperationException("The file could not be deleted from object storage.", exception);
        }
    }
}
