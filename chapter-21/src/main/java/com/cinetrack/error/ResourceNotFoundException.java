package com.cinetrack.error;

// Thrown by service methods when an entity cannot be found by its ID.
// Maps to HTTP 404 in GlobalExceptionHandler.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id);
    }
}
