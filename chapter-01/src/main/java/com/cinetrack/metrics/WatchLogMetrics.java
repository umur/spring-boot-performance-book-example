package com.cinetrack.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Encapsulates all Micrometer metrics for the watch-log feature.
// Injected into WatchLogService so metric recording stays out of business logic.
@Component
public class WatchLogMetrics {

    private final Counter watchlogsCreatedCounter;
    private final Counter duplicatesRejectedCounter;
    private final Timer tmdbLookupTimer;

    public WatchLogMetrics(MeterRegistry registry) {
        this.watchlogsCreatedCounter = Counter.builder("cinetrack.watchlogs.created")
                .description("Total number of watch logs successfully created")
                .tag("status", "success")
                .register(registry);

        this.duplicatesRejectedCounter = Counter.builder("cinetrack.watchlogs.rejected")
                .description("Total number of duplicate watch log attempts rejected")
                .tag("reason", "duplicate")
                .register(registry);

        this.tmdbLookupTimer = Timer.builder("cinetrack.tmdb.lookup")
                .description("Time spent fetching movie data from TMDB")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // Called after a watch log is persisted successfully.
    public void recordWatchLogCreated() {
        watchlogsCreatedCounter.increment();
    }

    // Called when a duplicate-movie attempt is rejected.
    public void recordDuplicate() {
        duplicatesRejectedCounter.increment();
    }

    // Called with the measured duration of a TMDB API call.
    public void recordTmdbLookup(Duration duration) {
        tmdbLookupTimer.record(duration);
    }
}
