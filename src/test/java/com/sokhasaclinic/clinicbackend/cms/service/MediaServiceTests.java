package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.MediaUploadResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.ImageTooLargeException;
import com.sokhasaclinic.clinicbackend.cms.exception.UnsupportedImageException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MediaServiceTests {

    private MediaRepository mediaRepository;
    private MediaStorage mediaStorage;
    private TransactionTemplate transactionTemplate;
    private MediaCleanupService mediaCleanupService;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaRepository = mock(MediaRepository.class);
        mediaStorage = mock(MediaStorage.class);
        transactionTemplate = mock(TransactionTemplate.class);
        mediaCleanupService = mock(MediaCleanupService.class);

        // Make transactionTemplate execute callback immediately
        doAnswer(invocation -> {
            var action = invocation.getArgument(0);
            if (action instanceof java.util.function.Consumer<?> consumer) {
                ((java.util.function.Consumer<TransactionStatus>) consumer).accept(new SimpleTransactionStatus());
            }
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        mediaService = new MediaService(mediaRepository, mediaStorage, transactionTemplate, mediaCleanupService);
    }

    private byte[] createSamplePng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should reject empty upload files")
    void rejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.png", "image/png", new byte[0]);
        assertThrows(CmsException.class, () -> mediaService.upload(emptyFile));
    }

    @Test
    @DisplayName("Should reject files exceeding the 5 MiB size limit")
    void rejectOversizedFile() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.png", "image/png", largeBytes);
        assertThrows(ImageTooLargeException.class, () -> mediaService.upload(largeFile));
    }

    @Test
    @DisplayName("Should reject non-image or unsupported formats")
    void rejectNonImageFile() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "Not an image".getBytes()
        );
        assertThrows(UnsupportedImageException.class, () -> mediaService.upload(textFile));
    }

    @Test
    @DisplayName("Should successfully upload and persist a valid PNG image")
    void uploadValidPng() throws Exception {
        byte[] pngBytes = createSamplePng(100, 100);
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", pngBytes);

        MediaUploadResponse response = mediaService.upload(file);

        assertNotNull(response.id());
        assertEquals("banner.png", response.fileName());
        assertEquals("image/png", response.mimeType());
        assertEquals(pngBytes.length, response.size());

        verify(mediaStorage).upload(anyString(), eq("image/png"), eq(pngBytes));
        verify(mediaRepository).saveAndFlush(any(CmsMedia.class));
        verify(mediaCleanupService).track(anyString());
    }
}
