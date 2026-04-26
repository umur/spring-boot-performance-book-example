package com.cinetrack.watchlog;

import com.cinetrack.security.AppUserDetails;
import com.cinetrack.watchlog.dto.CreateWatchLogRequest;
import com.cinetrack.watchlog.dto.WatchLogResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/watchlogs")
@RequiredArgsConstructor
public class WatchLogController {

    private final WatchLogService watchLogService;

    // POST /api/watchlogs
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WatchLogResponse> create(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody CreateWatchLogRequest request) {

        var response = watchLogService.create(request, principal.userId());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // GET /api/watchlogs/{id}
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public WatchLogResponse findById(@PathVariable Long id) {
        return watchLogService.findById(id);
    }

    // GET /api/watchlogs?minRating=3&maxRating=5
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<WatchLogResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Integer maxRating) {
        return watchLogService.findByUser(principal.userId(), minRating, maxRating);
    }

    // DELETE /api/watchlogs/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserDetails principal) {
        watchLogService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
