package com.cinetrack.health;

import com.cinetrack.tmdb.TmdbProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Custom Actuator health indicator that validates the TMDB API key by calling
// the lightweight /configuration/api endpoint. Exposed at /actuator/health.
@Slf4j
@Component("tmdb")
@RequiredArgsConstructor
public class TmdbHealthIndicator implements HealthIndicator {

    private final TmdbProperties tmdbProperties;

    @Override
    public Health health() {
        try {
            var client = RestClient.builder()
                    .baseUrl(tmdbProperties.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + tmdbProperties.apiKey())
                    .defaultHeader("Accept", "application/json")
                    .build();

            client.get()
                    .uri("/configuration/api")
                    .retrieve()
                    .toBodilessEntity();

            return Health.up()
                    .withDetail("baseUrl", tmdbProperties.baseUrl())
                    .build();
        } catch (Exception ex) {
            log.warn("TMDB health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("baseUrl", tmdbProperties.baseUrl())
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
