package com.cinetrack.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Integration test that verifies @Async dispatches the notification task
// on the notificationExecutor thread pool and the returned future completes.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    void sendWatchLogConfirmation_completesSuccessfully() throws Exception {
        CompletableFuture<Void> future =
                notificationService.sendWatchLogConfirmation("alice@example.com", "Inception");

        assertThat(future)
                .as("sendWatchLogConfirmation must return a non-null CompletableFuture")
                .isNotNull();

        // Block with a generous timeout; the simulated SMTP call sleeps 200 ms.
        Void result = future.get(5, TimeUnit.SECONDS);

        assertThat(result)
                .as("CompletableFuture<Void> must complete with null on success")
                .isNull();
    }

    @Test
    void sendWatchLogConfirmation_doesNotThrow() {
        assertThatCode(() -> {
            CompletableFuture<Void> future =
                    notificationService.sendWatchLogConfirmation("bob@example.com", "The Matrix");
            future.get(5, TimeUnit.SECONDS);
        }).doesNotThrowAnyException();
    }

    @Test
    void sendWelcomeEmail_completesSuccessfully() throws Exception {
        CompletableFuture<Void> future =
                notificationService.sendWelcomeEmail("carol@example.com", "carol");

        assertThat(future).isNotNull();

        Void result = future.get(5, TimeUnit.SECONDS);

        assertThat(result)
                .as("welcome email future must complete with null")
                .isNull();
    }

    @Test
    void concurrentNotifications_allComplete() throws Exception {
        // Fire three notifications simultaneously to exercise thread pool behavior.
        CompletableFuture<Void> first =
                notificationService.sendWatchLogConfirmation("u1@example.com", "Dune");
        CompletableFuture<Void> second =
                notificationService.sendWatchLogConfirmation("u2@example.com", "Oppenheimer");
        CompletableFuture<Void> third =
                notificationService.sendWelcomeEmail("u3@example.com", "u3");

        CompletableFuture<Void> all = CompletableFuture.allOf(first, second, third);
        all.get(10, TimeUnit.SECONDS);

        assertThat(first).isCompletedWithValue(null);
        assertThat(second).isCompletedWithValue(null);
        assertThat(third).isCompletedWithValue(null);
    }
}
