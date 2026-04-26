# Spring Boot 4 Performance in Practice — Code Examples

CinéTrack: a Spring Boot 4 monolith tuned from a 200 RPS baseline to 50× throughput across 28 chapters.

## Structure

Each directory is a self-contained Maven project corresponding to a chapter in the book. Later chapters build on earlier ones — the `final/` directory is the fully-tuned application.

| Directory | Chapter topic |
|-----------|---------------|
| `chapter-01` | Baseline: the untuned monolith |
| `chapter-02` | Observability: Micrometer, Prometheus, Grafana |
| `chapter-05` | Load testing with k6 |
| `chapter-06` | Flame graphs and async-profiler |
| `chapter-11` | Virtual threads (Project Loom) |
| `chapter-12` | HikariCP connection pool tuning |
| `chapter-13` | Database connection pool sizing formula |
| `chapter-15` | Caffeine L1 cache + W-TinyLFU |
| `chapter-16` | Two-level cache: Caffeine + Redis |
| `chapter-17` | Cache stampede and single-flight |
| `chapter-18` | HTTP client tuning |
| `chapter-19` | Async processing |
| `chapter-20` | JPA and Hibernate N+1 fixes |
| `chapter-21` | PostgreSQL index strategies |
| `chapter-22` | Query analysis with pg_stat_statements |
| `chapter-23` | Kafka producer batching |
| `chapter-24` | Kafka consumer offset strategies |
| `chapter-27` | GraalVM native image |
| `final` | Fully-tuned CinéTrack |

## Requirements

- Java 21
- Maven 3.8+
- Docker (for integration tests — Testcontainers spins up Postgres and Redis)

## Running tests

```bash
cd chapter-01
mvn verify
```

JaCoCo enforces 80% line coverage. All chapters pass.

## Book

*Spring Boot 4 Performance in Practice* by Umur Inan
