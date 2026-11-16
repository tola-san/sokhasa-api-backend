package com.sokhasaclinic.clinicbackend.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.ImageTooLargeException;
import com.sokhasaclinic.clinicbackend.cms.exception.UnsupportedImageException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
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

class MediaServiceTests {

    private MediaStorage mediaStorage;
    private MediaCleanupService mediaCleanupService;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaStorage = mock(MediaStorage.class);
        mediaCleanupService = mock(MediaCleanupService.class);
        mediaService = new MediaService(mediaStorage, mediaCleanupService);
    }

    static byte[] samplePng(int width, int height) throws IOException {
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
    @DisplayName("Rejects empty files before touching storage")
    void rejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("image", "test.png", "image/png", new byte[0]);
        assertThrows(CmsException.class, () -> mediaService.store(emptyFile));
        verify(mediaStorage, never()).upload(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Rejects files over 5 MiB")
    void rejectOversizedFile() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile("image", "large.png", "image/png", largeBytes);
        assertThrows(ImageTooLargeException.class, () -> mediaService.store(largeFile));
    }

    @Test
    @DisplayName("Rejects files that are not JPEG, PNG or WebP")
    void rejectNonImageFile() {
        MockMultipartFile textFile = new MockMultipartFile("image", "hello.txt", "text/plain", "Not an image".getBytes());
        assertThrows(UnsupportedImageException.class, () -> mediaService.store(textFile));
    }

    @Test
    @DisplayName("Tracks the key for cleanup before uploading a valid image")
    void storeValidPng() throws Exception {
        byte[] pngBytes = samplePng(100, 100);
        MockMultipartFile file = new MockMultipartFile("image", "C:\\photos\\clinic-team.png", "image/png", pngBytes);

        CmsMedia media = mediaService.store(file);

        assertEquals("clinic-team.png", media.getFileName());
        assertEquals("image/png", media.getMimeType());
        assertEquals(pngBytes.length, media.getSize());
        assertTrue(media.getObjectKey().startsWith("media/"));
        var order = inOrder(mediaCleanupService, mediaStorage);
        order.verify(mediaCleanupService).track(media.getObjectKey());
        order.verify(mediaStorage).upload(eq(media.getObjectKey()), eq("image/png"), eq(pngBytes));
    }

    @Test
    @DisplayName("Leaves a failed delete to the cleanup job")
    void deleteFailureIsRetriedLater() {
        doThrow(new RuntimeException("R2 down")).when(mediaStorage).delete("media/old");

        mediaService.deleteUnreferenced("media/old");

        verify(mediaCleanupService, never()).complete("media/old");
    }
}
