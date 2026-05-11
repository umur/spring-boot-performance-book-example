package com.cinetrack.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Validated request body for the login endpoint.
public record LoginRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "password is required")
        String password
) {}
