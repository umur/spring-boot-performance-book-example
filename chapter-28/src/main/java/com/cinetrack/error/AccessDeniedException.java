package com.cinetrack.error;

// Thrown when a user attempts to modify a resource they do not own.
// Maps to HTTP 403 in GlobalExceptionHandler.
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
