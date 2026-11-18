package com.sokhasaclinic.clinicbackend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.common.exception.ConflictException;
import com.sokhasaclinic.clinicbackend.common.exception.ErrorResponse;
import com.sokhasaclinic.clinicbackend.common.exception.GlobalExceptionHandler;
import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Test
    void handleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found", "PAGE_NOT_FOUND");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleApiException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PAGE_NOT_FOUND", response.getBody().type());
        assertEquals(404, response.getBody().status());
        assertEquals("/api/v1/test", response.getBody().instance());
    }

    @Test
    void handleConflictException() {
        ConflictException ex = new ConflictException("Conflict occurred", "SLUG_ALREADY_EXISTS");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleApiException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SLUG_ALREADY_EXISTS", response.getBody().type());
        assertEquals(409, response.getBody().status());
    }
}
