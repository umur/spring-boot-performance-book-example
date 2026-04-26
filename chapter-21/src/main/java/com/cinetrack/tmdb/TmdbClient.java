package com.cinetrack.tmdb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

// Handles all outbound calls to the TMDB REST API.
// Responses are cached with Caffeine/Redis to avoid hitting rate limits.
@Slf4j
@Component
public class TmdbClient {

    private final RestClient restClient;

    public TmdbClient(RestClient tmdbRestClient) {
        this.restClient = tmdbRestClient;
    }

    // Retries up to 3 times with exponential backoff on server errors.
    @Retryable(retryFor = TmdbUnavailableException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 500, multiplier = 2))
    @Cacheable(value = "tmdb-search", key = "#query + '-' + #page")
    public List<TmdbMovieResult> searchMovies(String query, int page) {
        log.debug("Calling TMDB search: query={}, page={}", query, page);
        try {
            var response = restClient.get()
                    .uri("/search/movie?query={q}&page={p}&language=en-US", query, page)
                    .retrieve()
                    .body(TmdbSearchResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new TmdbNotFoundException(query);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new TmdbRateLimitException("TMDB rate limit exceeded");
        } catch (Exception ex) {
            log.error("TMDB search failed for query={}: {}", query, ex.getMessage());
            throw new TmdbUnavailableException("TMDB is temporarily unavailable", ex);
        }
    }

    @Retryable(retryFor = TmdbUnavailableException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 500, multiplier = 2))
    @Cacheable(value = "tmdb-movies", key = "#tmdbId")
    public TmdbMovieResult getMovie(Long tmdbId) {
        log.debug("Calling TMDB movie details: tmdbId={}", tmdbId);
        try {
            return restClient.get()
                    .uri("/movie/{id}?language=en-US", tmdbId)
                    .retrieve()
                    .body(TmdbMovieResult.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new TmdbNotFoundException(tmdbId);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new TmdbRateLimitException("TMDB rate limit exceeded");
        } catch (Exception ex) {
            log.error("TMDB movie lookup failed for tmdbId={}: {}", tmdbId, ex.getMessage());
            throw new TmdbUnavailableException("TMDB is temporarily unavailable", ex);
        }
    }
}
