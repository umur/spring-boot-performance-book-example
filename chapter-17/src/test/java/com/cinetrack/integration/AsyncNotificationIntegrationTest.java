package com.cinetrack.integration;

import com.cinetrack.async.NotificationService;
import com.cinetrack.auth.AuthResponse;
import com.cinetrack.auth.RegisterRequest;
import com.cinetrack.watchlog.dto.CreateWatchLogRequest;
import com.cinetrack.watchlog.dto.WatchLogResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AsyncNotificationIntegrationTest {

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

    @Autowired
    private NotificationService notificationService;

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
    void sendWatchLogConfirmation_completesWithinTimeout() throws Exception {
        CompletableFuture<Void> future =
                notificationService.sendWatchLogConfirmation("async_it@example.com", "Inception");

        assertThat(future).isNotNull();
        future.get(5, TimeUnit.SECONDS);
        assertThat(future).isCompletedWithValue(null);
    }

    @Test
    void sendWelcomeEmail_completesViaAwaitility() {
        CompletableFuture<Void> future =
                notificationService.sendWelcomeEmail("welcome_it@example.com", "welcomeUser");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(future).isDone());

        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    void concurrentNotifications_allCompleteIndependently() throws Exception {
        CompletableFuture<Void> f1 =
                notificationService.sendWatchLogConfirmation("u1_it@example.com", "Dune");
        CompletableFuture<Void> f2 =
                notificationService.sendWatchLogConfirmation("u2_it@example.com", "Tenet");
        CompletableFuture<Void> f3 =
                notificationService.sendWelcomeEmail("u3_it@example.com", "u3_it");

        CompletableFuture.allOf(f1, f2, f3).get(10, TimeUnit.SECONDS);

        assertThat(f1).isCompletedWithValue(null);
        assertThat(f2).isCompletedWithValue(null);
        assertThat(f3).isCompletedWithValue(null);
    }

    @Test
    void watchLogCreation_doesNotBlockOnNotification() {
        wireMock.stubFor(get(urlPathEqualTo("/movie/80001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":80001,"title":"Barbie","overview":"...",
                                 "poster_path":null,"release_date":"2023-07-21","vote_average":7.0}
                                """)));

        String token = registerAndGetToken("async_wl_user", "async_wl@example.com");

        long start = System.currentTimeMillis();
        var createResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(80001L, "Barbie", LocalDate.of(2024, 1, 1), 4, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(elapsed).isLessThan(3_000L);
    }

    @Test
    void scheduledJobBean_isRegisteredInContext(
            @Autowired com.cinetrack.scheduled.TmdbCacheRefreshJob refreshJob) {
        assertThat(refreshJob).isNotNull();
        assertThat(notificationService).isNotNull();
    }
}
