package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.MediaUploadResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.ImageTooLargeException;
import com.sokhasaclinic.clinicbackend.cms.exception.UnsupportedImageException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service managing media file validation, storage, and persistence.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MiB
    private static final long MAX_PIXEL_COUNT = 40_000_000;


    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final TransactionTemplate transactionTemplate;
    private final MediaCleanupService mediaCleanupService;

    public MediaService(
            MediaRepository mediaRepository,
            MediaStorage mediaStorage,
            TransactionTemplate transactionTemplate,
            MediaCleanupService mediaCleanupService) {
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
        this.transactionTemplate = transactionTemplate;
        this.mediaCleanupService = mediaCleanupService;
    }

    /**
     * Validates, stores, and registers an uploaded media file.
     *
     * @param file the multipart file to upload
     * @return the metadata of the saved upload
     * @throws IOException if reading the file fails
     */


    public MediaUploadResponse upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw CmsException.invalid("An image is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageTooLargeException();
        }

        byte[] bytes = file.getBytes();
        String mimeType = validateAndExtractMimeType(bytes);
        String fileName = sanitizeFileName(file.getOriginalFilename());

        CmsMedia entity = new CmsMedia("media/" + UUID.randomUUID(), fileName, mimeType, bytes.length);

        mediaCleanupService.track(entity.getObjectKey());
        mediaStorage.upload(entity.getObjectKey(), mimeType, bytes);

        try {
            transactionTemplate.executeWithoutResult(status -> mediaRepository.saveAndFlush(entity));
        } catch (RuntimeException failure) {
            try {
                mediaStorage.delete(entity.getObjectKey());
            } catch (RuntimeException cleanupFailure) {
                log.error("Failed to delete orphaned object in storage: {}", entity.getObjectKey(), cleanupFailure);
            }
            throw failure;
        }

        return new MediaUploadResponse(entity.getId(), fileName, mimeType, bytes.length);
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
        if (name.isBlank() || name.length() > 255) {
            throw CmsException.invalid("Invalid filename.");
        }
        return name;
    }
}
