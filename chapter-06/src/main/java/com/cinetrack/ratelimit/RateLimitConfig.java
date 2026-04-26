package com.cinetrack.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Provides the Bucket4j ProxyManager backed by a Caffeine cache.
// Each distinct key (userId or IP address) gets its own rate-limit bucket
// stored in the Caffeine cache. Entries expire after the refill period so
// memory does not grow unbounded when users stop making requests.
@Configuration
public class RateLimitConfig {

    @Value("${rate-limit.refill-period-seconds:60}")
    private long refillPeriodSeconds;

    @Bean
    public ProxyManager<String> rateLimitProxyManager() {
        var caffeineBuilder = Caffeine.newBuilder()
                .maximumSize(100_000);

        return new CaffeineProxyManager<>(caffeineBuilder, Duration.ofSeconds(refillPeriodSeconds * 2));
    }
}
