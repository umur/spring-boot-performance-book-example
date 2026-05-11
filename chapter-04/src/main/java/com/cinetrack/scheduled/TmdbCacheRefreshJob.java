package com.cinetrack.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Periodic background job that evicts stale TMDB data from all caches.
// Runs nightly at 03:00 UTC to ensure movie metadata stays reasonably fresh
// without hammering the TMDB API during peak traffic hours.
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbCacheRefreshJob {

    private final CacheManager cacheManager;

    // cron = "second minute hour dayOfMonth month dayOfWeek"
    // This expression fires every day at 03:00:00 UTC.
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void refreshTopMoviesCache() {
        log.info("Starting nightly TMDB cache refresh");
        int evicted = 0;

        for (String cacheName : cacheManager.getCacheNames()) {
            if (cacheName.startsWith("tmdb-")) {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    evicted++;
                    log.debug("Evicted cache: {}", cacheName);
                }
            }
        }

        log.info("TMDB cache refresh complete: {} caches evicted", evicted);
    }
}
