package com.cinetrack.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

// Handles outbound notifications triggered by user actions in CinéTrack.
// Every public method is annotated @Async so Spring executes it on the
// notificationExecutor thread pool, keeping the caller's thread free.
@Slf4j
@Service
public class NotificationService {

    // Sends a confirmation email after a user logs a watched film.
    // The method returns CompletableFuture<Void> so callers can chain
    // callbacks or check completion status without blocking.
    @Async("notificationExecutor")
    public CompletableFuture<Void> sendWatchLogConfirmation(String email, String movieTitle) {
        log.info("Sending watch-log confirmation to {} for '{}'", email, movieTitle);
        try {
            // Simulate network latency of an SMTP call.
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Notification thread interrupted for email={}", email);
        }
        log.debug("Confirmation sent to {} for '{}'", email, movieTitle);
        return CompletableFuture.completedFuture(null);
    }

    // Sends a welcome email immediately after a user registers.
    @Async("notificationExecutor")
    public CompletableFuture<Void> sendWelcomeEmail(String email, String username) {
        log.info("Sending welcome email to {} (username={})", email, username);
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return CompletableFuture.completedFuture(null);
    }

    // Generic delivery method called from the Kafka consumer (chapter 24).
    // Synchronous so the consumer can ack on success and let Kafka redeliver
    // on failure.
    public void sendWatchLogNotification(String email, String subject, String body) {
        log.info("Delivering notification to {}: {}", email, subject);
        try {
            Thread.sleep(150);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending notification", ex);
        }
    }
}
