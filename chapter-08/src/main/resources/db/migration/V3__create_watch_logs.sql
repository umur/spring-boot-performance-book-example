CREATE TABLE watch_logs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    tmdb_id     BIGINT       NOT NULL,
    movie_title VARCHAR(255) NOT NULL,
    watched_date DATE        NOT NULL,
    rating      INTEGER      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    notes       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, tmdb_id)
);

CREATE INDEX idx_watch_logs_user_id ON watch_logs(user_id);
