package com.cinetrack.integration;

import com.cinetrack.auth.AuthResponse;
import com.cinetrack.auth.RegisterRequest;
import com.cinetrack.watchlog.dto.CreateWatchLogRequest;
import com.cinetrack.watchlog.dto.WatchLogResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MovieIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withStartupAttempts(3)
            .withReuse(true)
            .withDatabaseName("cinetrack_it")
            .withUsername("cinetrack")
            .withPassword("cinetrack");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withReuse(true)
            .withExposedPorts(6379);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.data-source-properties.sslmode", () -> "disable");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("tmdb.base-url", wireMock::baseUrl);
        registry.add("tmdb.api-key", () -> "test-key");
        registry.add("rate-limit.capacity", () -> "100");
        registry.add("rate-limit.refill-tokens", () -> "100");
        registry.add("rate-limit.refill-period-seconds", () -> "60");
    }

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = buildNoErrorRestTemplate();

    private static RestTemplate buildNoErrorRestTemplate() {
        var rt = new RestTemplate();
        rt.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.HttpStatusCode statusCode) { return false; }
        });
        return rt;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String registerAndLogin(String username, String email) {
        var req = new RegisterRequest(username, email, "Password1!");
        var resp = restTemplate.postForEntity(url("/api/auth/register"), req, AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().token();
    }

    private HttpHeaders bearerHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void fetchAndStore_newMovie_persistsDataAndReturnsCorrectFields() {
        wireMock.stubFor(get(urlPathMatching("/movie/9001.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":9001,"title":"Fight Club","overview":"The first rule.",\
                                "poster_path":"/abc.jpg","release_date":"1999-10-15","vote_average":8.4}
                                """)));

        String token = registerAndLogin("mv_store1", "mv_store1@example.com");

        var createResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(9001L, "Fight Club", LocalDate.of(2024, 1, 1), 5, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = createResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tmdbId()).isEqualTo(9001L);
        assertThat(body.movieTitle()).isEqualTo("Fight Club");

        wireMock.verify(1, getRequestedFor(urlPathMatching("/movie/9001.*")));
    }

    @Test
    void fetchAndStore_existingMovie_doesNotCallTmdbMoreThanOnce() {
        wireMock.stubFor(get(urlPathMatching("/movie/9002.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":9002,"title":"The Matrix","overview":"Red pill.",\
                                "poster_path":"/mat.jpg","release_date":"1999-03-31","vote_average":8.7}
                                """)));

        String tokenA = registerAndLogin("mv_cache_a", "mv_cache_a@example.com");
        String tokenB = registerAndLogin("mv_cache_b", "mv_cache_b@example.com");

        var respA = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(9002L, "The Matrix", LocalDate.of(2024, 2, 1), 5, null),
                        bearerHeaders(tokenA)),
                WatchLogResponse.class);
        assertThat(respA.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var respB = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(9002L, "The Matrix", LocalDate.of(2024, 2, 2), 4, null),
                        bearerHeaders(tokenB)),
                WatchLogResponse.class);
        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        wireMock.verify(1, getRequestedFor(urlPathMatching("/movie/9002.*")));
    }

    @Test
    void request_withCorrelationId_echoesItInResponse() {
        String correlationId = "test-corr-id-12345";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-Id", correlationId);

        var request = new RegisterRequest("mv_corr1", "mv_corr1@example.com", "Password1!");
        var response = restTemplate.exchange(
                url("/api/auth/register"), HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlationId);
    }

    @Test
    void request_withoutCorrelationId_generatesOne() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new RegisterRequest("mv_corr2", "mv_corr2@example.com", "Password1!");
        var response = restTemplate.exchange(
                url("/api/auth/register"), HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }
}
