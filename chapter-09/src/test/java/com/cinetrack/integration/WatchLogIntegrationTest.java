package com.cinetrack.integration;

import com.cinetrack.auth.AuthResponse;
import com.cinetrack.auth.RegisterRequest;
import com.cinetrack.health.TmdbHealthIndicator;
import com.cinetrack.movie.MovieService;
import com.cinetrack.scheduled.TmdbCacheRefreshJob;
import com.cinetrack.watchlog.dto.CreateWatchLogRequest;
import com.cinetrack.watchlog.dto.WatchLogResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WatchLogIntegrationTest {

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

    @Autowired
    private TmdbHealthIndicator tmdbHealthIndicator;

    @Autowired
    private TmdbCacheRefreshJob tmdbCacheRefreshJob;

    @Autowired
    private MovieService movieService;

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

    private void stubTmdbMovie550() {
        wireMock.stubFor(get(urlPathMatching("/movie/550.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":550,"title":"Fight Club","overview":"Rules.",\
                                "poster_path":"/abc.jpg","release_date":"1999-10-15","vote_average":8.4}
                                """)));
    }

    private void stubTmdbMovie551() {
        wireMock.stubFor(get(urlPathMatching("/movie/551.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":551,"title":"Pulp Fiction","overview":"Gold watch.",\
                                "poster_path":"/def.jpg","release_date":"1994-10-14","vote_average":8.9}
                                """)));
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void create_success_returns201WithLocationAndCorrectFields() {
        stubTmdbMovie550();
        String token = registerAndLogin("wl_create1", "wl_create1@example.com");

        var request = new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 3, 10), 5, "Masterpiece");
        var response = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)),
                WatchLogResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();

        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.tmdbId()).isEqualTo(550L);
        assertThat(body.movieTitle()).isEqualTo("Fight Club");
        assertThat(body.watchedDate()).isEqualTo(LocalDate.of(2024, 3, 10));
        assertThat(body.rating()).isEqualTo(5);
        assertThat(body.notes()).isEqualTo("Masterpiece");
        assertThat(body.createdAt()).isNotNull();
    }

    @Test
    void create_duplicate_returns409() {
        stubTmdbMovie550();
        String token = registerAndLogin("wl_dup1", "wl_dup1@example.com");

        var request = new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 3, 10), 4, null);

        var first = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)),
                WatchLogResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var second = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(token)),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void findById_success_returns200WithCorrectFields() {
        stubTmdbMovie550();
        String token = registerAndLogin("wl_findby1", "wl_findby1@example.com");

        var createResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 5, 1), 3, "Good"),
                        bearerHeaders(token)),
                WatchLogResponse.class);
        Long id = createResp.getBody().id();

        var getResp = restTemplate.exchange(
                url("/api/watchlogs/" + id), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                WatchLogResponse.class);

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = getResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(id);
        assertThat(body.tmdbId()).isEqualTo(550L);
        assertThat(body.rating()).isEqualTo(3);
    }

    @Test
    void findById_notFound_returns404() {
        String token = registerAndLogin("wl_notfound1", "wl_notfound1@example.com");

        var response = restTemplate.exchange(
                url("/api/watchlogs/99999"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_returnsAllWatchLogsForAuthenticatedUser() {
        stubTmdbMovie550();
        stubTmdbMovie551();
        String token = registerAndLogin("wl_list1", "wl_list1@example.com");

        restTemplate.exchange(url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 1, 1), 5, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);

        restTemplate.exchange(url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(551L, "Pulp Fiction", LocalDate.of(2024, 2, 1), 4, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);

        var listResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                new ParameterizedTypeReference<List<WatchLogResponse>>() {});

        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).isNotNull();
        assertThat(listResp.getBody().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void list_withRatingFilter_returnsOnlyMatchingEntries() {
        stubTmdbMovie550();
        stubTmdbMovie551();
        String token = registerAndLogin("wl_filter1", "wl_filter1@example.com");

        restTemplate.exchange(url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 1, 1), 5, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);

        restTemplate.exchange(url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(551L, "Pulp Fiction", LocalDate.of(2024, 2, 1), 2, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);

        var filteredResp = restTemplate.exchange(
                url("/api/watchlogs?minRating=5&maxRating=5"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                new ParameterizedTypeReference<List<WatchLogResponse>>() {});

        assertThat(filteredResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filteredResp.getBody()).isNotNull();
        assertThat(filteredResp.getBody())
                .allSatisfy(log -> assertThat(log.rating()).isBetween(5, 5));
    }

    @Test
    void delete_success_returns204AndSubsequentGetReturns404() {
        stubTmdbMovie550();
        String token = registerAndLogin("wl_delete1", "wl_delete1@example.com");

        var createResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 6, 1), 4, null),
                        bearerHeaders(token)),
                WatchLogResponse.class);
        Long id = createResp.getBody().id();

        var deleteResp = restTemplate.exchange(
                url("/api/watchlogs/" + id), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(token)),
                Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getAfterDelete = restTemplate.exchange(
                url("/api/watchlogs/" + id), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                String.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_otherUsersLog_returns403() {
        stubTmdbMovie550();
        String tokenA = registerAndLogin("wl_own_a", "wl_own_a@example.com");
        String tokenB = registerAndLogin("wl_own_b", "wl_own_b@example.com");

        var createResp = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.POST,
                new HttpEntity<>(
                        new CreateWatchLogRequest(550L, "Fight Club", LocalDate.of(2024, 7, 1), 5, null),
                        bearerHeaders(tokenA)),
                WatchLogResponse.class);
        Long id = createResp.getBody().id();

        var deleteResp = restTemplate.exchange(
                url("/api/watchlogs/" + id), HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(tokenB)),
                String.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticated_request_isRejected() {
        var response = restTemplate.exchange(
                url("/api/watchlogs"), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void tmdbHealthIndicator_returnsUp_whenTmdbResponds200() {
        wireMock.stubFor(get(urlPathMatching("/configuration/api.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        Health health = tmdbHealthIndicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsKey("baseUrl");
    }

    @Test
    void tmdbHealthIndicator_returnsDown_whenTmdbReturnsServerError() {
        wireMock.stubFor(get(urlPathMatching("/configuration/api.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        Health health = tmdbHealthIndicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void tmdbCacheRefreshJob_refreshTopMoviesCache_completesWithoutError() {
        tmdbCacheRefreshJob.refreshTopMoviesCache();
        assertThat(tmdbCacheRefreshJob).isNotNull();
    }

    @Test
    void movieService_search_returnsResultsFromTmdb() {
        wireMock.stubFor(get(urlPathMatching("/search/movie.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"page":1,"totalPages":1,"totalResults":1,"results":[
                                  {"id":9010,"title":"Inception","overview":"Dreams.",
                                   "poster_path":"/inc.jpg","release_date":"2010-07-16","vote_average":8.8}
                                ]}
                                """)));

        var results = movieService.search("Inception", 1);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).isEqualTo("Inception");
    }

    @Test
    void movieService_search_returnsEmptyList_whenTmdbReturnsNoResults() {
        wireMock.stubFor(get(urlPathMatching("/search/movie.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"page\":1,\"totalPages\":0,\"totalResults\":0,\"results\":[]}")));

        var results = movieService.search("xyzunknownfilm", 1);

        assertThat(results).isEmpty();
    }
}
