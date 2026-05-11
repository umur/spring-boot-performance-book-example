package com.cinetrack.kafka;

import com.cinetrack.async.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// Chapter 24 — Kafka consumer for CinéTrack notification events.
// Replaces the @Async SMTP path: events arrive from Kafka, this listener
// invokes the existing NotificationService to deliver. Manual acknowledgment
// gives at-least-once delivery; SMTP failures roll back the offset commit so
// the next poll re-delivers (24.5).
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${cinetrack.kafka.notifications-topic:notifications}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleNotification(NotificationEventPublisher.NotificationEvent event,
                                   Acknowledgment ack) {
        try {
            notificationService.sendWatchLogNotification(
                    event.email(), event.subject(), event.body());
            // Only ack after the email is sent. Failures here will surface as
            // a redelivery on the next poll (at-least-once semantics).
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to deliver notification for userId={}: {}",
                    event.userId(), ex.getMessage());
            // Don't ack — let Kafka redeliver. In production we'd route to a
            // dead-letter topic after N retries; covered in 24.7.
            throw ex;
        }
    }
}
