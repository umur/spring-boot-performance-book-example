package com.cinetrack.config;

import com.cinetrack.tmdb.TmdbProperties;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// Builds the RestClient bean used by TmdbClient.
// The request interceptor propagates the current correlation ID as an outbound
// header so TMDB-side logs can be correlated with CinéTrack logs.
@Configuration
public class RestClientConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Bean
    public RestClient tmdbRestClient(TmdbProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Accept", "application/json")
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().add(CORRELATION_HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
