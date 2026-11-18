package com.sokhasaclinic.clinicbackend.media.service;

import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import com.sokhasaclinic.clinicbackend.common.exception.ValidationException;
import com.sokhasaclinic.clinicbackend.media.dto.MediaUploadResponse;
import com.sokhasaclinic.clinicbackend.media.model.Media;
import com.sokhasaclinic.clinicbackend.media.repository.MediaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MiB

    private final MediaRepository mediaRepository;

    public MediaService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public MediaUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded file cannot be empty.", "FILE_EMPTY");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File size exceeds maximum limit of 5 MiB.", "FILE_TOO_LARGE");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new ValidationException("Unsupported file format. Supported formats: JPEG, PNG, WebP.", "UNSUPPORTED_MEDIA_TYPE");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        UUID id = UUID.randomUUID();
        String storageKey = "media/" + id + "_" + originalFilename;

        Media media = Media.builder()
                .id(id)
                .fileName(originalFilename)
                .mimeType(mimeType)
                .fileSize(file.getSize())
                .storageKey(storageKey)
                .url("/api/v1/public/media/" + id)
                .createdAt(Instant.now())
                .build();

        return MediaUploadResponse.fromEntity(mediaRepository.save(media));
    }

    @Transactional(readOnly = true)
    public List<MediaUploadResponse> listMedia() {
        return mediaRepository.findAll().stream()
                .map(MediaUploadResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaUploadResponse getMedia(UUID id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found: " + id, "MEDIA_NOT_FOUND"));
        return MediaUploadResponse.fromEntity(media);
    }

    @Transactional
    public void deleteMedia(UUID id) {
        if (!mediaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Media not found: " + id, "MEDIA_NOT_FOUND");
        }
        mediaRepository.deleteById(id);
    }
}
