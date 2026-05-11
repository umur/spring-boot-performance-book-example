package com.cinetrack.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

// Producer wrapper that publishes CinéTrack notification events.
// Replaces the synchronous SMTP path from chapter 1: events go to Kafka, a
// downstream consumer (chapter 24) handles the actual email send. Decoupling
// removes SMTP latency from the request thread entirely.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${cinetrack.kafka.notifications-topic:notifications}")
    private String notificationsTopic;

    // Returns a future so callers can chain on success/failure without blocking.
    // Partition key is the userId: keeps per-user events ordered (23.6).
    public CompletableFuture<?> publishNotification(Long userId, NotificationEvent event) {
        return kafkaTemplate.send(notificationsTopic, String.valueOf(userId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish notification for userId={}: {}",
                                userId, ex.getMessage());
                    } else {
                        log.debug("Notification published: userId={}, partition={}, offset={}",
                                userId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public record NotificationEvent(
            Long userId,
            String email,
            String subject,
            String body,
            long timestamp
    ) {}
}
