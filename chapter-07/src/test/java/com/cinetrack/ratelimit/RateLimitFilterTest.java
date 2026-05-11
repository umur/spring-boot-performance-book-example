package com.cinetrack.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Plain JUnit 5 test for RateLimitFilter.
// Uses a real Caffeine-backed ProxyManager to exercise the full bucket lifecycle
// without starting a Spring context. Field values normally set by @Value are
// injected via ReflectionTestUtils.
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        var caffeineBuilder = Caffeine.newBuilder()
                .maximumSize(1_000);

        ProxyManager<String> proxyManager = new CaffeineProxyManager<>(caffeineBuilder, Duration.ofSeconds(120));
        filter = new RateLimitFilter(proxyManager);

        // Inject @Value fields that Spring would normally populate.
        ReflectionTestUtils.setField(filter, "capacity", 5L);
        ReflectionTestUtils.setField(filter, "refillTokens", 5L);
        ReflectionTestUtils.setField(filter, "refillPeriodSeconds", 60L);
    }

    @Test
    void firstRequest_isAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/movies/search");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus())
                .as("first request within capacity must be passed through (not 429)")
                .isNotEqualTo(429);
        assertThat(response.getHeader("X-Rate-Limit-Remaining"))
                .as("remaining tokens header must be present after a successful consume")
                .isNotNull();
    }

    @Test
    void requestsWithinCapacity_areAllAllowed() throws Exception {
        // Fire exactly `capacity` requests; all should succeed.
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/watchlogs");
            request.setRemoteAddr("10.0.0.2");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus())
                    .as("request #" + i + " must succeed (status != 429)")
                    .isNotEqualTo(429);
        }
    }

    @Test
    void requestBeyondCapacity_isRejectedWith429() throws Exception {
        String remoteAddr = "10.0.0.3";

        // Exhaust the 5-token bucket.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/watchlogs");
            request.setRemoteAddr(remoteAddr);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        // The 6th request must be rejected.
        MockHttpServletRequest overLimitRequest = new MockHttpServletRequest("GET", "/api/watchlogs");
        overLimitRequest.setRemoteAddr(remoteAddr);
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(overLimitRequest, overLimitResponse, chain);

        assertThat(overLimitResponse.getStatus())
                .as("a request that exceeds bucket capacity must receive HTTP 429")
                .isEqualTo(429);
        assertThat(overLimitResponse.getHeader("Retry-After"))
                .as("a 429 response must include a Retry-After header")
                .isNotNull();
    }

    @Test
    void rejectedResponse_containsJsonBody() throws Exception {
        String remoteAddr = "10.0.0.4";

        // Exhaust bucket.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/watchlogs");
            req.setRemoteAddr(remoteAddr);
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest overLimit = new MockHttpServletRequest("GET", "/api/watchlogs");
        overLimit.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(overLimit, response, new MockFilterChain());

        String body = response.getContentAsString();
        assertThat(body)
                .as("429 response body must contain a status field")
                .contains("429");
        assertThat(body)
                .as("429 response body must mention Too Many Requests")
                .contains("Too Many Requests");
    }

    @Test
    void actuatorPath_isNotFiltered() throws Exception {
        // shouldNotFilter returns true for non-/api/ paths, so the filter skips them.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // shouldNotFilter returns true, so doFilterInternal is never called.
        boolean shouldSkip = filter.shouldNotFilter(request);

        assertThat(shouldSkip)
                .as("actuator endpoints must be excluded from rate limiting")
                .isTrue();
    }

    @Test
    void apiPath_isSubjectToFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/watchlogs");

        boolean shouldSkip = filter.shouldNotFilter(request);

        assertThat(shouldSkip)
                .as("/api/* paths must be subject to rate limiting")
                .isFalse();
    }

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        // Exhaust bucket for IP A.
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/watchlogs");
            req.setRemoteAddr("10.0.0.6");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP B should still have a full bucket.
        MockHttpServletRequest requestFromB = new MockHttpServletRequest("GET", "/api/watchlogs");
        requestFromB.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse responseFromB = new MockHttpServletResponse();

        filter.doFilter(requestFromB, responseFromB, new MockFilterChain());

        assertThat(responseFromB.getStatus())
                .as("a different IP must have its own independent bucket and must not be rate-limited")
                .isNotEqualTo(429);
    }
}
