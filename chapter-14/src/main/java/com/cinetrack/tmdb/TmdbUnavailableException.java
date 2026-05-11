package com.cinetrack.tmdb;

// Thrown when TMDB returns a 5xx error or is unreachable.
// Triggers the Spring Retry mechanism in TmdbClient.
public class TmdbUnavailableException extends RuntimeException {

    public TmdbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
