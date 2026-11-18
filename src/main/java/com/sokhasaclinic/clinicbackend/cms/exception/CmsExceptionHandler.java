package com.sokhasaclinic.clinicbackend.cms.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Centralized exception handler for CMS and public content controllers providing RFC 7807
 * problem details.
 */
@RestControllerAdvice(basePackages = {
        "com.sokhasaclinic.clinicbackend.cms.controller",
        "com.sokhasaclinic.clinicbackend.clinicservice.controller"
})
public class CmsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CmsExceptionHandler.class);

    @ExceptionHandler(CmsException.class)
    public ResponseEntity<Map<String, Object>> handleCmsException(CmsException ex, HttpServletRequest request) {
        // Server-side failures (for example STORAGE_UNAVAILABLE when R2 is not configured) must be
        // visible in the deployment logs; client errors stay quiet.
        if (ex.getStatus() >= 500) {
            log.error("CMS {} {} failed: {} {}", request.getMethod(), request.getRequestURI(),
                    ex.getType(), ex.getMessage(), ex);
        }
        return buildErrorResponse(ex.getStatus(), ex.getType(), ex.getMessage(), ex.getErrors(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class,
            // A translations part that is not sent as application/json.
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Map<String, Object>> handleValidationException(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(400, "VALIDATION_ERROR", "One or more fields are invalid.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(413, "IMAGE_TOO_LARGE", "Maximum image size is 5 MiB.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(Exception ex, HttpServletRequest request) {
        return buildErrorResponse(409, "CONFLICT", "The request conflicts with the current data.", request);
    }

    @ExceptionHandler(SdkException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(Exception ex, HttpServletRequest request) {
        log.error("CMS {} {} failed: media storage error: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage(), ex);
        return buildErrorResponse(503, "STORAGE_UNAVAILABLE", "Media storage is temporarily unavailable.", request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            int status, String type, String detail, HttpServletRequest request) {
        return buildErrorResponse(status, type, detail, List.of(), request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            int status, String type, String detail, List<Map<String, Object>> errors, HttpServletRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        String title = httpStatus != null ? httpStatus.getReasonPhrase() : "Error";

        Map<String, Object> body = Map.of(
                "type", type,
                "title", title,
                "status", status,
                "detail", detail,
                "instance", request.getRequestURI(),
                "timestamp", Instant.now(),
                "errors", errors
        );

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
