package com.cinetrack.integration;

import com.cinetrack.auth.AuthResponse;
import com.cinetrack.auth.LoginRequest;
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
class AuthIntegrationTest {

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

    @Test
    void register_success_returns201WithToken() {
        var request = new RegisterRequest("auth_reg1", "auth_reg1@example.com", "Password1!");

        var response = restTemplate.postForEntity(url("/api/auth/register"), request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().userId()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("auth_reg1");
    }

    @Test
    void register_duplicateEmail_returns409() {
        var first = new RegisterRequest("auth_dupemail_a", "auth_dupemail@example.com", "Password1!");
        restTemplate.postForEntity(url("/api/auth/register"), first, AuthResponse.class);

        var second = new RegisterRequest("auth_dupemail_b", "auth_dupemail@example.com", "Password1!");
        var response = restTemplate.postForEntity(url("/api/auth/register"), second, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_duplicateUsername_returns409() {
        var first = new RegisterRequest("auth_dupuser", "auth_dupuser_a@example.com", "Password1!");
        restTemplate.postForEntity(url("/api/auth/register"), first, AuthResponse.class);

        var second = new RegisterRequest("auth_dupuser", "auth_dupuser_b@example.com", "Password1!");
        var response = restTemplate.postForEntity(url("/api/auth/register"), second, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_invalidPassword_tooShort_returns422() {
        var request = new RegisterRequest("auth_badpw", "auth_badpw@example.com", "short");

        var response = restTemplate.postForEntity(url("/api/auth/register"), request, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void register_blankEmail_returns422() {
        var request = new RegisterRequest("auth_blankemail", "", "Password1!");

        var response = restTemplate.postForEntity(url("/api/auth/register"), request, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void login_success_returns200WithToken() {
        var registerReq = new RegisterRequest("auth_login1", "auth_login1@example.com", "Password1!");
        restTemplate.postForEntity(url("/api/auth/register"), registerReq, AuthResponse.class);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var loginReq = new LoginRequest("auth_login1@example.com", "Password1!");

        var response = restTemplate.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(loginReq, headers), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    void login_wrongPassword_returns401() {
        var registerReq = new RegisterRequest("auth_wrongpw", "auth_wrongpw@example.com", "Password1!");
        restTemplate.postForEntity(url("/api/auth/register"), registerReq, AuthResponse.class);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var loginReq = new LoginRequest("auth_wrongpw@example.com", "WrongPassword!");

        var response = restTemplate.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(loginReq, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_unknownEmail_returns401() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var loginReq = new LoginRequest("nobody@example.com", "Password1!");

        var response = restTemplate.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(loginReq, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
