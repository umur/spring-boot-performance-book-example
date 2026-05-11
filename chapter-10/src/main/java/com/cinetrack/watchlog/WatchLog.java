package com.cinetrack.watchlog;

import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Records a single viewing event by a user for a specific TMDB movie.
@Entity
@Table(name = "watch_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tmdb_id"}))
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
