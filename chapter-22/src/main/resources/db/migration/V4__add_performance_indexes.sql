-- Chapter 21: indexes added after EXPLAIN ANALYZE evidence from chapter 19.
-- Each index addresses a query plan that was doing a Seq Scan + Sort.

-- Composite covering the list endpoint:
--   SELECT ... FROM watch_logs WHERE user_id = ? ORDER BY watched_date DESC
-- Without this, Postgres reads every row for the user and sorts; with it,
-- the index walk delivers rows already ordered (21.4 covers index-only scans).
CREATE INDEX IF NOT EXISTS idx_watch_logs_user_id_watched_date
    ON watch_logs (user_id, watched_date DESC);

-- Top-rated query (chapter 19's findTopRatedByUser). Composite ordering
-- matches the ORDER BY exactly so the planner skips the Sort node entirely.
CREATE INDEX IF NOT EXISTS idx_watch_logs_user_rating_watched
    ON watch_logs (user_id, rating DESC, watched_date DESC);

-- Partial index for "highly rated" queries. Most users only care about ratings
-- 4 and 5; making the index partial keeps it small and the lookup fast (21.5).
CREATE INDEX IF NOT EXISTS idx_watch_logs_high_rated
    ON watch_logs (user_id, watched_date DESC)
    WHERE rating >= 4;

-- Existence checks: existsByUserIdAndTmdbId is hit on every POST /watchlogs.
-- The unique constraint already creates an index, but on (user_id, tmdb_id)
-- it's the obvious key — verifying for clarity in the migration history.
CREATE INDEX IF NOT EXISTS idx_watch_logs_user_tmdb
    ON watch_logs (user_id, tmdb_id);

-- Movie cache key: tmdb_id is queried by MovieService.fetchAndStore.
-- Without an index Postgres scans the whole movies table on every cache miss.
CREATE INDEX IF NOT EXISTS idx_movies_tmdb_id
    ON movies (tmdb_id);

-- Email lookup is the hot path for login. Expression index lets us match
-- case-insensitively without sequential scanning the user table (21.6).
CREATE INDEX IF NOT EXISTS idx_app_users_email_lower
    ON app_users (LOWER(email));
