CREATE TABLE movies (
    id           BIGSERIAL    PRIMARY KEY,
    tmdb_id      BIGINT       NOT NULL UNIQUE,
    title        VARCHAR(255) NOT NULL,
    overview     TEXT,
    poster_path  VARCHAR(512),
    release_date DATE,
    vote_average DOUBLE PRECISION,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
