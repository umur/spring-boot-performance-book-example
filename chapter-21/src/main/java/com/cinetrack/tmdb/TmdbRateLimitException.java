package com.cinetrack.tmdb;

// Thrown when TMDB responds with HTTP 429 Too Many Requests.
// Not retried automatically -- callers should surface this as HTTP 429.
public class TmdbRateLimitException extends RuntimeException {

    public TmdbRateLimitException(String message) {
        super(message);
    }
}
