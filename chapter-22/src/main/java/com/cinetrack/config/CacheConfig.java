package com.cinetrack.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

// Two-level cache (16.6): Caffeine L1 in-process, Redis L2 shared across
// instances. CompositeCacheManager checks L1 first; on miss, the Redis
// manager handles the lookup. Writes go to L1 first, then L2 — same path,
// just opposite direction. The L1 keeps p99 low for hot keys; the L2 keeps
// hit rate high across the fleet so a rolling deploy doesn't reset every cache.
@Configuration
@Profile("!prod")
public class CacheConfig {

    // L1 — Caffeine, in-process, small and fast.
    // Sizes intentionally smaller than chapter 15 because the L2 absorbs misses;
    // we don't need every node to hold every hot entry.
    @Bean
    public CacheManager caffeineL1CacheManager() {
        var manager = new CaffeineCacheManager();
        manager.registerCustomCache("tmdb-movies",
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .recordStats()
                        .build());
        manager.registerCustomCache("tmdb-search",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
                        .build());
        return manager;
    }

    // L2 — Redis, shared across instances, larger and longer-lived.
    // Per-cache TTLs differ because the workloads differ (16.4).
    @Bean
    public CacheManager redisL2CacheManager(RedisConnectionFactory connectionFactory) {
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration("tmdb-movies",
                        defaults.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration("tmdb-search",
                        defaults.entryTtl(Duration.ofMinutes(30)))
                .build();
    }

    // Composite — Spring queries the managers in order, returning the first
    // hit. setFallbackToNoOpCache(true) ensures @Cacheable methods that name
    // a cache neither manager knows about don't blow up at runtime.
    @Bean
    @Primary
    public CacheManager cacheManager(CacheManager caffeineL1CacheManager,
                                     CacheManager redisL2CacheManager) {
        var composite = new CompositeCacheManager(
                caffeineL1CacheManager, redisL2CacheManager);
        composite.setFallbackToNoOpCache(false);
        return composite;
    }
}
