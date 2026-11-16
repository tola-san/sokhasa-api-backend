package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service responsible for asynchronous tracking and cleanup of uncommitted or orphaned media uploads.
 */
@Service
public class MediaCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupService.class);

    private final JdbcTemplate jdbcTemplate;
    private final MediaStorage mediaStorage;

    public MediaCleanupService(JdbcTemplate jdbcTemplate, MediaStorage mediaStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaStorage = mediaStorage;
    }

    /**
     * Registers an object key to be inspected for cleanup if not committed within an hour.
     */
    public void track(String objectKey) {
        jdbcTemplate.update(
                "insert into clinic.cms_upload_cleanup(object_key, retry_after) values (?, now() + interval '1 hour')",
                objectKey
        );
    }

    /**
     * Completes and removes a tracked object key once successfully associated with an entity.
     */
    public void complete(String objectKey) {
        jdbcTemplate.update("delete from clinic.cms_upload_cleanup where object_key = ?", objectKey);
    }

    /**
     * Periodic background job that cleans up orphaned uploads whose retry time has expired.
     */
    @Scheduled(fixedDelay = 300000)
    public void retryCleanup() {
        List<String> keys = jdbcTemplate.queryForList(
                "select object_key from clinic.cms_upload_cleanup where retry_after < now() limit 100",
                String.class
        );

        for (String key : keys) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "select count(*) from clinic.cms_media where object_key = ?",
                        Integer.class,
                        key
                );
                if (count != null && count == 0) {
                    mediaStorage.delete(key);
                }
                complete(key);
            } catch (RuntimeException failure) {
                log.warn("Media cleanup will retry object {}: {}", key, failure.getMessage());
            }
        }
    }
}
