package com.sokhasaclinic.clinicbackend.media.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.media.dto.MediaUploadResponse;
import com.sokhasaclinic.clinicbackend.media.service.MediaService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaUploadResponse> uploadMedia(@RequestParam("file") MultipartFile file) {
        return ApiResponse.of(mediaService.upload(file));
    }

    @GetMapping
    public ApiResponse<List<MediaUploadResponse>> listMedia() {
        return ApiResponse.of(mediaService.listMedia());
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaUploadResponse> getMedia(@PathVariable UUID id) {
        return ApiResponse.of(mediaService.getMedia(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable UUID id) {
        mediaService.deleteMedia(id);
    }
}
