package com.cinetrack.watchlog.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

// Validated on entry into the controller via @Valid; all fields are required.
public record CreateWatchLogRequest(

        @NotNull(message = "tmdbId is required")
        Long tmdbId,

        @NotBlank(message = "movieTitle is required")
        @Size(max = 255)
        String movieTitle,

        @NotNull(message = "watchedDate is required")
        @PastOrPresent(message = "watchedDate cannot be in the future")
        LocalDate watchedDate,

        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be at least 1")
        @Max(value = 5, message = "rating must be at most 5")
        Integer rating,

        @Size(max = 2000, message = "notes must not exceed 2000 characters")
        String notes
) {}
