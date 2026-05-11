package com.cinetrack.tmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds tmdb.* keys from application.yml into a typed configuration record.
@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(String baseUrl, String apiKey) {}
