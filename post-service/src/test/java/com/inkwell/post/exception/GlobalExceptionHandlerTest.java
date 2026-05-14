package com.inkwell.post.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleCustomException() {
        CustomException ex = new CustomException("Error", HttpStatus.BAD_REQUEST);
        ResponseEntity<Map<String, Object>> response = handler.handleCustomException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error", response.getBody().get("message"));
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    void handlePostNotFoundException() {
        PostNotFoundException ex = new PostNotFoundException("Not found");
        ResponseEntity<Map<String, Object>> response = handler.handlePostNotFoundException(ex);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not found", response.getBody().get("message"));
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    void handleInvalidPostException() {
        InvalidPostException ex = new InvalidPostException("Invalid");
        ResponseEntity<Map<String, Object>> response = handler.handleInvalidPostException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid", response.getBody().get("message"));
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    void handleAlreadyLikedException() {
        AlreadyLikedException ex = new AlreadyLikedException("Already liked");
        ResponseEntity<Map<String, Object>> response = handler.handleAlreadyLikedException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Already liked", response.getBody().get("message"));
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    void handleGenericException() {
        Exception ex = new Exception("General error");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("General error"));
        assertEquals(false, response.getBody().get("success"));
    }
}
