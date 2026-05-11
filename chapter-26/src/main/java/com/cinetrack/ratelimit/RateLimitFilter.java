package com.cinetrack.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.function.Supplier;

// Enforces per-user (or per-IP for anonymous callers) rate limits using Bucket4j.
// Each bucket allows `capacity` tokens, refilled at a rate of `refillTokens`
// per `refillPeriodSeconds`. When a bucket runs empty the request is rejected
// with HTTP 429 and a Retry-After header.
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;

    @Value("${rate-limit.capacity:100}")
    private long capacity;

    @Value("${rate-limit.refill-tokens:100}")
    private long refillTokens;

    @Value("${rate-limit.refill-period-seconds:60}")
    private long refillPeriodSeconds;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit requests to the API. Actuator and static paths are excluded.
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String key = resolveKey(request);
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillTokens, Duration.ofSeconds(refillPeriodSeconds))
                        .build())
                .build();

        var bucket = proxyManager.builder().build(key, configSupplier);
        var probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("Rate limit exceeded for key={}, retryAfter={}s", key, waitSeconds);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Too Many Requests\"," +
                    "\"detail\":\"Rate limit exceeded. Retry after " + waitSeconds + " seconds.\"}");
        }
    }

    // Authenticated users are identified by their email; anonymous callers by IP.
    // X-Forwarded-For is checked first so that requests routed through a reverse
    // proxy or load balancer are rate-limited by the real client IP, not the
    // proxy's address.
    private String resolveKey(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String)) {
            return "user:" + auth.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
