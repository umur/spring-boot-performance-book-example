# Chapter 1 — Baseline CinéTrack

The chapter-1 baseline **is the final state of CinéTrack from *Spring Boot 4 in Practice*.** To keep code single-source, this directory does not duplicate it.

## Start here

```bash
# From the top of the books monorepo:
cd ../../../../spring-boot/code/cinetrack/final
docker-compose up -d
```

That's your chapter-1 system: the monolith the book tunes from scratch.

## What chapter 1 measures

Chapter 1 runs a k6 baseline against that system to establish the starting numbers we'll improve across the next 27 chapters. The baseline script lives in this directory once chapter 1 is written.

## What later chapters do

From chapter 2 onward, `code/cinetrack/chapter-NN/` holds a cumulative snapshot of the system with that chapter's tuning changes applied. `code/cinetrack/final/` at the end of the book is CinéTrack running at roughly 50× its chapter-1 throughput on the same hardware.
