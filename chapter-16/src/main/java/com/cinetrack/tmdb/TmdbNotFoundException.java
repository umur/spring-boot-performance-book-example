package com.cinetrack.tmdb;

// Thrown when TMDB returns a 404 for a movie lookup.
// Maps to HTTP 404 in GlobalExceptionHandler.
public class TmdbNotFoundException extends RuntimeException {

    public TmdbNotFoundException(long tmdbId) {
        super("Movie not found in TMDB with id: " + tmdbId);
    }

    public TmdbNotFoundException(String query) {
        super("No TMDB results found for query: " + query);
    }
}
