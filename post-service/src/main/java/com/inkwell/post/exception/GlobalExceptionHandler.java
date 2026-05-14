package com.inkwell.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePostNotFoundException(PostNotFoundException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidPostException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPostException(InvalidPostException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AlreadyLikedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyLikedException(AlreadyLikedException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(CustomException ex) {
        return buildErrorResponse(ex.getMessage(), ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("success", false);
        errorBody.put("message", "Validation failed");
        errorBody.put("errors", fieldErrors);
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Map<String, Object>> handleBindingException(ServletRequestBindingException ex) {
        log.error("Missing required request element: {}", ex.getMessage());
        return buildErrorResponse("Missing or invalid header/parameter: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught: ", ex);
        return buildErrorResponse("An unexpected error occurred: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return new ResponseEntity<>(error, status);
    }
}
