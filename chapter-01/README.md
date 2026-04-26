# Chapter 1 — Baseline CinéTrack

The starting point for the entire book: the final state of CinéTrack from
*Spring Boot 4 in Practice* (chapter 16), copied here verbatim and locked
behind a JaCoCo 80% line-coverage gate so every later tuning chapter runs
against an identical baseline.

## Run it

```bash
docker compose up -d            # Postgres + Redis + MailHog
mvn spring-boot:run             # service on :8080
```

## Test it

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn verify
```

47 integration tests pass against real Postgres + Redis containers; WireMock
intercepts TMDB. Current line coverage: **89%**.

## What chapter 1 measures

Chapter 1 is the only chapter without tuning — it establishes the latency
histogram and throughput baseline that every later chapter compares against.
The k6 baseline script lives in `chapter-05/loadtest/k6/baseline.js`.

## What later chapters do

Each `chapter-NN/` is a **cumulative snapshot** — chapter 11 contains every
chapter 02–06 change plus virtual threads; chapter 22 contains everything up
to and including read-replica routing. `final/` is the end-state at the close
of chapter 28, ~50× faster than this baseline.

| Chapter | What it adds                                                    |
| ------- | --------------------------------------------------------------- |
| 02      | Micrometer histograms + SLO buckets                             |
| 05      | k6 baseline + spike scripts under `loadtest/k6/`                |
| 06      | Gatling baseline + spike scripts under `loadtest/gatling/`      |
| 11      | `spring.threads.virtual.enabled=true`                           |
| 12      | Tomcat / @Async pool sizing                                     |
| 13      | HikariCP formula-based tuning + leak detection                  |
| 15      | Per-cache Caffeine config with refresh-ahead                    |
| 16      | Two-level cache: Caffeine L1 + Redis L2                         |
| 17      | HTTP/2 + gzip compression                                       |
| 18      | Jackson 3 hot-path tuning                                       |
| 19      | EXPLAIN-driven repository methods                               |
| 20      | `@BatchSize`, `default_batch_fetch_size`, ordered inserts       |
| 21      | New Flyway migration with composite/partial/expression indexes  |
| 22      | Read-replica routing via `AbstractRoutingDataSource` + PgBouncer|
| 23      | Kafka producer with batching, lz4, idempotence                  |
| 24      | Kafka consumer with concurrency + manual ack                    |
| 27      | Native-image Maven profile                                      |
