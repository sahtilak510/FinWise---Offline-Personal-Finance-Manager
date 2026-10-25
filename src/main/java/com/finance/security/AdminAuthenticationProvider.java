package com.finance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminAuthenticationProvider implements AuthenticationProvider {
    private final String username;
    private final String passwordHash;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthenticationProvider(@Value("${spring.security.user.name}") String username,
                                       @Value("${spring.security.user.password}") String password,
                                       PasswordEncoder passwordEncoder) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Admin username and password must be configured");
        }
        this.username = username;
        this.passwordEncoder = passwordEncoder;
        this.passwordHash = passwordEncoder.encode(password);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!username.equals(authentication.getName())) {
            return null;
        }
        if (authentication.getCredentials() == null
                || !passwordEncoder.matches(authentication.getCredentials().toString(), passwordHash)) {
            throw new BadCredentialsException("Invalid admin username or password");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
