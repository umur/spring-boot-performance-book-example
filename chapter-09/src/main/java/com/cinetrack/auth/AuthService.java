package com.cinetrack.auth;

import com.cinetrack.error.DuplicateResourceException;
import com.cinetrack.security.JwtService;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }
        if (appUserRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken: " + request.username());
        }

        var user = AppUser.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        var saved = appUserRepository.save(user);
        var token = jwtService.generateToken(saved.getEmail());
        return new AuthResponse(token, saved.getId(), saved.getDisplayUsername());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var user = appUserRepository.findByEmail(request.email())
                .orElseThrow();
        var token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getDisplayUsername());
    }
}
