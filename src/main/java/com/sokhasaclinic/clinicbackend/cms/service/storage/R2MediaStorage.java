package com.sokhasaclinic.clinicbackend.cms.service.storage;

import com.sokhasaclinic.clinicbackend.cms.exception.StorageUnavailableException;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 / AWS S3 implementation of MediaStorage.
 */
@Component
public class R2MediaStorage implements MediaStorage {

    private final String bucket;
    private final S3Client client;
    private final S3Presigner signer;

    public R2MediaStorage(
            @Value("${R2_BUCKET:}") String bucket,
            @Value("${R2_ENDPOINT:}") String endpoint,
            @Value("${R2_ACCESS_KEY_ID:}") String accessKeyId,
            @Value("${R2_SECRET_ACCESS_KEY:}") String secretAccessKey) {
        String cleanBucket = sanitize(bucket);
        String cleanEndpoint = sanitize(endpoint);
        String cleanAccessKey = sanitize(accessKeyId);
        String cleanSecret = sanitize(secretAccessKey);

        this.bucket = cleanBucket;

        if (cleanBucket.isBlank() || cleanEndpoint.isBlank() || cleanAccessKey.isBlank() || cleanSecret.isBlank()) {
            this.client = null;
            this.signer = null;
            return;
        }

        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(cleanAccessKey, cleanSecret));
        var configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(cleanEndpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();

        this.signer = S3Presigner.builder()
                .endpointOverride(URI.create(cleanEndpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private void requireConfigured() {
        if (client == null) {
            throw new StorageUnavailableException("Media storage is not configured.");
        }
    }

    @Override
    public void upload(String key, String mimeType, byte[] bytes) {
        requireConfigured();
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(mimeType)
                        .build(),
                RequestBody.fromBytes(bytes)
        );
    }

    @Override
    public void delete(String key) {
        requireConfigured();
        client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        );
    }

    @Override
    public String signedUrl(String key) {
        requireConfigured();
        return signer.presignGetObject(r -> r
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(g -> g.bucket(bucket).key(key))
        ).url().toString();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
        if (signer != null) {
            signer.close();
        }
    }
}
