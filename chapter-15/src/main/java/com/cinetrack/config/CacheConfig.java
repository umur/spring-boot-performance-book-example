package com.cinetrack.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

// Per-cache Caffeine configuration with explicit Window TinyLFU policies (15.3).
// Sizing is tuned to the workload: search results churn fast and have high
// cardinality, while movie details are stable and reused often.
// recordStats() enables hit-rate, eviction-rate, and load-time metrics that
// Micrometer exports to Prometheus (15.7).
@Configuration
@Profile("!prod")
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager();

        // tmdb-movies: long-lived, high-reuse details. Refresh-ahead (15.5)
        // would normally renew entries in the background: but with Spring's
        // @Cacheable abstraction we'd need a CacheLoader adapter. Sticking with
        // expire-after-write keeps the example self-contained; chapter 15
        // covers the LoadingCache adapter pattern in 15.5.
        manager.registerCustomCache("tmdb-movies",
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(6, TimeUnit.HOURS)
                        .recordStats()
                        .build());

        // tmdb-search: short-lived, high-churn. Smaller cache, shorter TTL, no
        // refresh-ahead: search queries are too varied for refresh to pay off.
        manager.registerCustomCache("tmdb-search",
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .recordStats()
                        .build());

        return manager;
    }
}
