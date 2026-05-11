package com.cinetrack.security;

import com.cinetrack.user.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Adapter that wraps AppUser for Spring Security's authentication pipeline.
// Exposed as the @AuthenticationPrincipal in secured controllers.
public record AppUserDetails(Long userId, String email, String password) implements UserDetails {

    public static AppUserDetails from(AppUser user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    // Spring Security uses getUsername() as the principal identity key.
    // CinéTrack authenticates by email, so email is returned here.
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired()     { return true; }

    @Override
    public boolean isAccountNonLocked()      { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()               { return true; }
}
