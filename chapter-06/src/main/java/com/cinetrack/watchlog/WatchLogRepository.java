package com.cinetrack.watchlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchLogRepository extends JpaRepository<WatchLog, Long> {

    // Finds all watch logs for a given user, ordered newest watched date first.
    List<WatchLog> findByUserIdOrderByWatchedDateDesc(Long userId);

    // Checks whether a user has already logged a specific TMDB movie.
    boolean existsByUserIdAndTmdbId(Long userId, Long tmdbId);

    Optional<WatchLog> findByIdAndUserId(Long id, Long userId);

    // Paginated query used by the list endpoint.
    Page<WatchLog> findByUserId(Long userId, Pageable pageable);

    // Inclusive rating range filter for a single user.
    @Query("""
            SELECT w FROM WatchLog w
            WHERE w.user.id = :userId
              AND w.rating >= :min
              AND w.rating <= :max
            ORDER BY w.watchedDate DESC
            """)
    List<WatchLog> findByUserIdWithRatingRange(
            @Param("userId") Long userId,
            @Param("min") int min,
            @Param("max") int max);
}
