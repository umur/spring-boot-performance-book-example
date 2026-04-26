package com.cinetrack.watchlog.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Immutable response DTO returned from every WatchLog endpoint.
public record WatchLogResponse(
        Long id,
        Long userId,
        Long tmdbId,
        String movieTitle,
        LocalDate watchedDate,
        Integer rating,
        String notes,
        LocalDateTime createdAt
) {}
