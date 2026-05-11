package com.cinetrack.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

// Chapter 24 — concurrency, fetch sizing, and back-pressure for the
// notifications topic. Concurrency=4 means the listener factory creates 4
// threads; with a 12-partition topic each thread handles ~3 partitions.
// Bigger isn't better — see 24.2 for why over-allocating threads can starve
// the consumer group's rebalance protocol.
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:cinetrack-notifications}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, NotificationEventPublisher.NotificationEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Fetch sizing (24.3) — bigger fetch.min.bytes coalesces network round-trips
        // at the cost of latency. For notifications we don't care about p99 fetch
        // latency, so wait up to 500ms or 64KB, whichever comes first.
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 64 * 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // Hard cap on the per-poll record count. The poll-loop is what holds the
        // partition lease — too many records per poll and we miss the heartbeat
        // window (24.4 covers max.poll.interval.ms tuning).
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 60_000);

        // Trust DTOs from our own producer.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.cinetrack.kafka");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,
            NotificationEventPublisher.NotificationEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationEventPublisher.NotificationEvent> consumerFactory) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String,
                NotificationEventPublisher.NotificationEvent>();
        factory.setConsumerFactory(consumerFactory);
        // 4 listener threads. Match this to the partition count divisor — too few
        // and you under-utilize partitions; too many and the extra threads idle.
        factory.setConcurrency(4);

        // Manual acks per batch — gives us at-least-once semantics with bounded
        // re-delivery on consumer failure (24.5). AckMode.MANUAL_IMMEDIATE acks
        // each Acknowledgment.acknowledge() call as soon as the listener returns.
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
