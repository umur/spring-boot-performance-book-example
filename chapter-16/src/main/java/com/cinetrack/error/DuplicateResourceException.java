package com.cinetrack.error;

// Thrown when a user tries to log a movie they have already logged.
// Maps to HTTP 409 in GlobalExceptionHandler.
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
