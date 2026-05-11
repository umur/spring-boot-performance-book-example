# Spring Boot 4 Performance in Practice

> CinéTrack from a 200 RPS baseline to 50× throughput: 28 chapters of measurement-driven tuning.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring&logoColor=white) ![GraalVM](https://img.shields.io/badge/GraalVM-native-E85027) ![License: MIT](https://img.shields.io/badge/License%3A_MIT-MIT-blue)

Companion code for the book **Spring Boot 4 Performance in Practice** by [Umur Inan](https://umurinan.com).

## About the book

A measurement-driven performance book. We start with a Spring Boot 4 monolith. **CinéTrack**. Failing its p99 SLO at 200 RPS, and across 28 chapters we use JFR, async-profiler, JVM tuning (G1, ZGC, generational ZGC), virtual threads, structured concurrency, connection pool tuning, HTTP/2, JIT and reachability metadata, native AOT with GraalVM, queueing theory, and capacity planning to get the same service to 50× the throughput on the same hardware. Every chapter ends with numbers.

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

- `chapter-01/ … chapter-28/`: cumulative CinéTrack state at the end of each chapter, with the chapter's JVM configuration, profiling scripts, k6 load profile, and benchmark results
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

## About the author

I'm Umur Inan. I write books about Spring Boot, Java, distributed systems, and the practices that make production reliable.

📚 **More writing and books → [umurinan.com](https://umurinan.com)**

## License

MIT. See [LICENSE](LICENSE).
