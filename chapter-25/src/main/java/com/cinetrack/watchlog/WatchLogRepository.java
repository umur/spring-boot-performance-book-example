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

    // Top-rated movies for a user. EXPLAIN ANALYZE on the chapter-01 baseline
    // shows a Sort node spilling to disk for users with >10K logs (19.6).
    // Rewriting as a LIMIT'd query lets Postgres use the
    // (user_id, rating DESC, watched_date DESC) composite index added in
    // chapter 21 and skip the sort entirely: Bitmap Heap Scan + Index Scan,
    // no Sort node.
    @Query("""
            SELECT w FROM WatchLog w
            WHERE w.user.id = :userId
              AND w.rating >= :minRating
            ORDER BY w.rating DESC, w.watchedDate DESC
            """)
    List<WatchLog> findTopRatedByUser(
            @Param("userId") Long userId,
            @Param("minRating") int minRating,
            Pageable pageable);

    // EXPLAIN-driven count: for a user-scoped count we don't need DISTINCT or a
    // subquery. The (user_id) B-tree index supports an Index Only Scan when the
    // visibility map is up to date, dropping latency from milliseconds to
    // microseconds (19.3 covers BUFFERS analysis).
    @Query("SELECT COUNT(w) FROM WatchLog w WHERE w.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
