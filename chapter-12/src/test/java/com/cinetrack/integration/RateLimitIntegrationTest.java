package com.cinetrack.integration;

import com.cinetrack.auth.AuthResponse;
import com.cinetrack.auth.RegisterRequest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateLimitIntegrationTest {

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
        registry.add("rate-limit.capacity", () -> "5");
        registry.add("rate-limit.refill-tokens", () -> "5");
        registry.add("rate-limit.refill-period-seconds", () -> "60");
    }

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
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

    private String registerAndGetToken(String username, String email) {
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

    @Test
    void requestsWithinLimit_areAllAccepted() {
        String token = registerAndGetToken("rl_within", "rl_within@example.com");

        for (int i = 0; i < 5; i++) {
            var resp = restTemplate.exchange(
                    url("/api/watchlogs"), HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)), String.class);
            assertThat(resp.getStatusCode())
                    .as("request #%d should not be rate-limited".formatted(i + 1))
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void requestBeyondLimit_receives429() {
        String token = registerAndGetToken("rl_exceed", "rl_exceed@example.com");

        for (int i = 0; i < 5; i++) {
            restTemplate.exchange(url("/api/watchlogs"), HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)), String.class);
        }

        var overLimitResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), String.class);

        assertThat(overLimitResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void rateLimitedResponse_includesRetryAfterHeader() {
        String token = registerAndGetToken("rl_header", "rl_header@example.com");

        for (int i = 0; i < 5; i++) {
            restTemplate.exchange(url("/api/watchlogs"), HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)), String.class);
        }

        var resp = restTemplate.exchange(url("/api/watchlogs"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void actuatorEndpoint_isNotRateLimited() {
        for (int i = 0; i < 10; i++) {
            var resp = restTemplate.getForEntity(url("/actuator/health"), String.class);
            assertThat(resp.getStatusCode())
                    .as("actuator request #%d must not be rate-limited".formatted(i + 1))
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void differentUsers_haveIndependentBuckets() {
        String tokenA = registerAndGetToken("rl_ind_a", "rl_ind_a@example.com");
        String tokenB = registerAndGetToken("rl_ind_b", "rl_ind_b@example.com");

        for (int i = 0; i < 5; i++) {
            restTemplate.exchange(url("/api/watchlogs"), HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(tokenA)), String.class);
        }
        var overLimitA = restTemplate.exchange(url("/api/watchlogs"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenA)), String.class);
        assertThat(overLimitA.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        var respB = restTemplate.exchange(url("/api/watchlogs"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(tokenB)), String.class);
        assertThat(respB.getStatusCode())
                .as("user B's bucket must be independent of user A's")
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
