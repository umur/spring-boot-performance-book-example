package com.cinetrack.tmdb;

import java.util.List;

// Wraps the TMDB paginated search response.
public record TmdbSearchResponse(
        int page,
        List<TmdbMovieResult> results,
        int totalPages,
        int totalResults
) {}
