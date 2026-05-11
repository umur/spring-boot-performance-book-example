package com.cinetrack.watchlog;

import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Records a single viewing event by a user for a specific TMDB movie.
//
// @BatchSize at the entity level controls how many WatchLog rows Hibernate
// pre-fetches when initializing a lazy collection (20.5). The right size is
// "the number of associated entities you'd expect on a typical page": 50
// matches CinéTrack's pagination default.
@Entity
@Table(name = "watch_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tmdb_id"}))
@BatchSize(size = 50)
@Getter
@Setter
@NoArgsConstructor
public class WatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Column(name = "movie_title", nullable = false)
    private String movieTitle;

    @Column(name = "watched_date", nullable = false)
    private LocalDate watchedDate;

    // Rating is 1-5 inclusive; validated at the controller layer.
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
