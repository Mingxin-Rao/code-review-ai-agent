package com.codeguardian.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handle resource-not-found exceptions (404)
     */
    @ExceptionHandler({NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(Exception e) {
        log.warn("Resource not found: {}", e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Resource not found");
        response.put("message", "The requested resource does not exist");
        response.put("status", HttpStatus.NOT_FOUND.value());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Handle file-upload size-limit-exceeded exceptions
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("File upload size limit exceeded: {}", e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", "File too large");
        response.put("message", "The uploaded file exceeds the maximum allowed size (50MB)");
        response.put("status", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * Handle not-logged-in exceptions
     *
     * <p>Returns 401 when a protected resource is accessed without an established login.<br/>
     * Returns 401 when a protected resource is accessed without an established login session.</p>
     *
     * @param e the not-logged-in exception
     * @return a 401 response containing an error message
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<?> handleNotLoginException(NotLoginException e, jakarta.servlet.http.HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String xhr = request.getHeader("X-Requested-With");
        String uri = request.getRequestURI();

        boolean wantsJson = (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
                || (xhr != null && xhr.equalsIgnoreCase("XMLHttpRequest"))
                || (uri != null && uri.startsWith("/api"));

        if (wantsJson) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Not logged in");
            response.put("message", e.getMessage());
            response.put("status", HttpStatus.UNAUTHORIZED.value());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/login")
                .build();
    }

    /**
     * Handle insufficient-permission exceptions
     *
     * <p>Returns 403 when the logged-in principal lacks the required permission.<br/>
     * Returns 403 when the current login session lacks the required permission.</p>
     *
     * @param e the insufficient-permission exception
     * @return a 403 response containing an error message
     */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Map<String, Object>> handleNotPermissionException(NotPermissionException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "No permission");
        response.put("message", e.getMessage());
        response.put("status", HttpStatus.FORBIDDEN.value());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
    
    /**
     * Handle invalid-argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Invalid argument", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Invalid argument");
        response.put("message", e.getMessage());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
    
    /**
     * Handle runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime error", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Server error");
        response.put("message", e.getMessage());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
    
    /**
     * Handle argument-validation exceptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        log.error("Validation error", e);
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("errors", errors);
        response.put("status", HttpStatus.BAD_REQUEST.value());
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
    
    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unknown error", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Internal server error");
        response.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}
