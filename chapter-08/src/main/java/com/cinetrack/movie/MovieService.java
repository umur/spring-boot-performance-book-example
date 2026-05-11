package com.cinetrack.movie;

import com.cinetrack.tmdb.TmdbClient;
import com.cinetrack.tmdb.TmdbMovieResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;

    // Searches TMDB and returns raw results without persisting them.
    public List<TmdbMovieResult> search(String query, int page) {
        return tmdbClient.searchMovies(query, page);
    }

    // Fetches a movie from TMDB and upserts it into the local database.
    @Transactional
    public Movie fetchAndStore(Long tmdbId) {
        return movieRepository.findByTmdbId(tmdbId)
                .orElseGet(() -> {
                    var result = tmdbClient.getMovie(tmdbId);
                    var movie = new Movie();
                    movie.setTmdbId(result.id());
                    movie.setTitle(result.title());
                    movie.setOverview(result.overview());
                    movie.setPosterPath(result.posterPath());
                    movie.setReleaseDate(result.releaseDate());
                    movie.setVoteAverage(result.voteAverage());
                    return movieRepository.save(movie);
                });
    }
}
