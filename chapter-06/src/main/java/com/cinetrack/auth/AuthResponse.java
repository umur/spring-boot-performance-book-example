package com.cinetrack.auth;

// Returned from both /register and /login on success.
public record AuthResponse(String token, Long userId, String username) {}
