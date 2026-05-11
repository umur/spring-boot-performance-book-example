package com.cinetrack.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;

// Hot-path Jackson tuning (chapter 18). Spring Boot 4 ships Jackson 3, which
// has bytecode-generated property accessors built in: the historical
// Afterburner / Blackbird modules are no longer required. Wins now come from
// disabling features that allocate or reflect unnecessarily.
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                // FAIL_ON_UNKNOWN_PROPERTIES is already disabled by Spring Boot's
                // default: explicit here for clarity. Forces no parse-time check
                // on every request, letting the parser skip unknown fields fast.
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // Sorting properties on every write costs a Map sort per object : 
                // pure waste for an HTTP response that is never diffed.
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
    }
}
