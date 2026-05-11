package com.cinetrack.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

// Represents a single movie entry in a TMDB search or details response.
public record TmdbMovieResult(
        Long id,
        String title,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("release_date") LocalDate releaseDate,
        @JsonProperty("vote_average") Double voteAverage
) {}
