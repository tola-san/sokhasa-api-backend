package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.ImageTooLargeException;
import com.sokhasaclinic.clinicbackend.cms.exception.UnsupportedImageException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates CMS images and stores their bytes in private object storage.
 * Callers persist the returned {@link CmsMedia} in their own transaction.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MiB
    private static final long MAX_PIXEL_COUNT = 40_000_000;

    private final MediaStorage mediaStorage;
    private final MediaCleanupService mediaCleanupService;

    public MediaService(MediaStorage mediaStorage, MediaCleanupService mediaCleanupService) {
        this.mediaStorage = mediaStorage;
        this.mediaCleanupService = mediaCleanupService;
    }

    /**
     * Validates the image and uploads it under a new object key. The key is tracked for cleanup
     * first, so the object is deleted later if no {@code cms_media} row ever references it.
     *
     * @return an unsaved media entity describing the uploaded object
     */
    public CmsMedia store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CmsException.invalid("An image is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageTooLargeException();
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw CmsException.invalid("The image cannot be read.");
        }
        String mimeType = validateAndExtractMimeType(bytes);
        String fileName = sanitizeFileName(file.getOriginalFilename());

        CmsMedia media = new CmsMedia("media/" + UUID.randomUUID(), fileName, mimeType, bytes.length);
        mediaCleanupService.track(media.getObjectKey());
        mediaStorage.upload(media.getObjectKey(), mimeType, bytes);
        return media;
    }

    /**
     * Best-effort removal of an uploaded object whose database write failed. The cleanup job
     * retries if this delete fails.
     */
    public void discard(CmsMedia media) {
        try {
            mediaStorage.delete(media.getObjectKey());
            mediaCleanupService.complete(media.getObjectKey());
        } catch (RuntimeException failure) {
            log.warn("Uploaded object {} will be removed by the cleanup job: {}",
                    media.getObjectKey(), failure.getMessage());
        }
    }

    /**
     * Best-effort removal of an object that is no longer referenced. The key must already be
     * scheduled with {@link MediaCleanupService#scheduleNow(String)} so failures are retried.
     */
    public void deleteUnreferenced(String objectKey) {
        try {
            mediaStorage.delete(objectKey);
            mediaCleanupService.complete(objectKey);
        } catch (RuntimeException failure) {
            log.warn("Replaced object {} will be removed by the cleanup job: {}", objectKey, failure.getMessage());
        }
    }

    private String validateAndExtractMimeType(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UnsupportedImageException();
            }

            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String mime = switch (format) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> throw new UnsupportedImageException();
                };

                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXEL_COUNT) {
                    throw CmsException.invalid("Image dimensions exceed the decoding limit.");
                }

                reader.read(0);
                return mime;
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw CmsException.invalid("The image cannot be decoded.");
        }
    }

    private String sanitizeFileName(String originalFilename) {
        String name = Objects.requireNonNullElse(originalFilename, "image");
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank()) {
            name = "image";
        }
        if (name.length() > 255) {
            throw CmsException.invalid("Invalid filename.");
        }
        return name;
    }
}
