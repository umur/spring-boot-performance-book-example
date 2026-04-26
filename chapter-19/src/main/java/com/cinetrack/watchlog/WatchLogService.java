package com.cinetrack.watchlog;

import com.cinetrack.error.AccessDeniedException;
import com.cinetrack.error.DuplicateResourceException;
import com.cinetrack.error.ResourceNotFoundException;
import com.cinetrack.metrics.WatchLogMetrics;
import com.cinetrack.movie.MovieService;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.watchlog.dto.CreateWatchLogRequest;
import com.cinetrack.watchlog.dto.WatchLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchLogService {

    private final WatchLogRepository watchLogRepository;
    private final AppUserRepository appUserRepository;
    private final MovieService movieService;
    private final WatchLogMetrics watchLogMetrics;

    @Transactional
    public WatchLogResponse create(CreateWatchLogRequest request, Long userId) {
        if (watchLogRepository.existsByUserIdAndTmdbId(userId, request.tmdbId())) {
            watchLogMetrics.recordDuplicate();
            throw new DuplicateResourceException(
                    "You have already logged movie with tmdbId: " + request.tmdbId());
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", userId));

        // Measure TMDB lookup time and record it in the timer metric.
        var start = Instant.now();
        movieService.fetchAndStore(request.tmdbId());
        watchLogMetrics.recordTmdbLookup(Duration.between(start, Instant.now()));

        var watchLog = new WatchLog();
        watchLog.setUser(user);
        watchLog.setTmdbId(request.tmdbId());
        watchLog.setMovieTitle(request.movieTitle());
        watchLog.setWatchedDate(request.watchedDate());
        watchLog.setRating(request.rating());
        watchLog.setNotes(request.notes());

        var saved = watchLogRepository.save(watchLog);
        watchLogMetrics.recordWatchLogCreated();
        log.info("WatchLog created: id={}, userId={}, tmdbId={}", saved.getId(), userId, request.tmdbId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WatchLogResponse findById(Long id) {
        var watchLog = watchLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WatchLog", id));
        return toResponse(watchLog);
    }

    @Transactional(readOnly = true)
    public List<WatchLogResponse> findByUser(Long userId, Integer minRating, Integer maxRating) {
        List<WatchLog> results;

        if (minRating != null && maxRating != null) {
            results = watchLogRepository.findByUserIdWithRatingRange(userId, minRating, maxRating);
        } else {
            results = watchLogRepository.findByUserIdOrderByWatchedDateDesc(userId);
        }

        return results.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id, Long requestingUserId) {
        var watchLog = watchLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WatchLog", id));

        if (!watchLog.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You do not own this watch log");
        }

        watchLogRepository.delete(watchLog);
        log.info("WatchLog deleted: id={}, userId={}", id, requestingUserId);
    }

    private WatchLogResponse toResponse(WatchLog w) {
        return new WatchLogResponse(
                w.getId(),
                w.getUser().getId(),
                w.getTmdbId(),
                w.getMovieTitle(),
                w.getWatchedDate(),
                w.getRating(),
                w.getNotes(),
                w.getCreatedAt()
        );
    }
}
