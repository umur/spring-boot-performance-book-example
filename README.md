# Spring Boot 4 Performance in Practice

> CinéTrack from a 200 RPS baseline to 50x throughput: 28 chapters of measurement-driven tuning.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring&logoColor=white) ![GraalVM](https://img.shields.io/badge/GraalVM-native-E85027) ![License: MIT](https://img.shields.io/badge/License%3A_MIT-MIT-blue)

Companion code for **Spring Boot 4 Performance in Practice** by [Umur Inan](https://umurinan.com).

## About the book

A measurement-driven performance book. We start with a Spring Boot 4 monolith. **CinéTrack**. Failing its p99 SLO at 200 RPS, and across 28 chapters we use JFR, async-profiler, JVM tuning (G1, ZGC, generational ZGC), virtual threads, structured concurrency, connection pool tuning, HTTP/2, JIT and reachability metadata, native AOT with GraalVM, queueing theory, and capacity planning to get the same service to 50x the throughput on the same hardware. Every chapter ends with numbers.

## Who this is for

- Spring Boot engineers whose services pass load tests but fall apart at production traffic
- Anyone who has guessed at JVM settings, HikariCP pool sizes, or Kafka consumer threads
- Senior engineers preparing for capacity planning conversations and SLO negotiations

## Chapters

1. Latency is a cliff, not a gradient
2. Micrometer timers and histograms in Spring Boot 4
3. Java Flight Recorder in anger
4. async-profiler and reading flame graphs correctly
5. Load testing with k6
6. Load testing with Gatling
7. Garbage collectors in Java 21: G1 vs ZGC
8. Heap sizing, region sizing, and pause budgets
9. JIT, tiered compilation, and warmup in long-running services
10. Native memory, direct buffers, and off-heap caches
11. Virtual threads in Spring Boot 4: when they win and when they don't
12. Thread pools: Tomcat, @Async, Scheduler
13. HikariCP and the pool-sizing formula
14. Reactive with WebFlux: an honest benchmark
15. Caching with Caffeine
16. Caching with Redis
17. HTTP/2, keep-alive, and response compression
18. Serialization on the hot path
19. Reading EXPLAIN ANALYZE for Spring Boot queries
20. Hibernate performance patterns
21. Indexing strategies for CineTrack queries
22. PostgreSQL tuning for read-heavy Spring Boot apps
23. Kafka producer throughput tuning
24. Kafka consumer concurrency and backpressure
25. Queueing theory: Little's Law and the utilization-latency curve
26. Capacity planning and cost per request
27. GraalVM native image: honest trade-offs
28. CineTrack at 50x: the final benchmark and retrospective

## Prerequisites

- Java 21 LTS ([Temurin](https://adoptium.net))
- Maven 3.9+
- Docker & Docker Compose (Postgres, Redis, Kafka)
- [k6](https://k6.io) for load testing
- [async-profiler](https://github.com/async-profiler/async-profiler) for profiling chapters
- GraalVM 21+ for native chapters

## Quick start

```bash
git clone https://github.com/umur/spring-boot-performance-book-example
cd spring-boot-performance-book-example/final
docker compose up -d
mvn spring-boot:run
```

## Layout

One self-contained Spring Boot project per chapter:

- `chapter-01/ ... chapter-28/`: cumulative CinéTrack state at the end of each chapter, with the chapter's JVM configuration, profiling scripts, k6 load profile, and benchmark results
- `final/`: the complete tuned application with the full benchmark suite

## Stack

- Java 21 LTS (default); Java 25 LTS for chapters that need preview features
- Spring Boot 4
- PostgreSQL 16
- Redis 7
- Kafka 4
- JFR, async-profiler, JMC for profiling
- k6 for load testing
- GraalVM (native chapters)
- Spring AOT + reachability metadata

## Related books

- [Spring Boot 4 in Practice](https://github.com/umur/spring-boot-example): the baseline CineTrack application before performance tuning begins
- [PostgreSQL: From MVCC to Production](https://github.com/umur/db-book-example): database-level performance covered in depth, complements the Hibernate and query chapters here
- [Production Caching](https://github.com/umur/production-caching-example): caching strategy covered here in two chapters; full treatment there

## About the author

I'm Umur Inan. I write production-focused books about Java, Spring Boot, distributed systems, and everything that makes software reliable at scale.

[umurinan.com](https://umurinan.com)

## License

MIT. See [LICENSE](LICENSE).
