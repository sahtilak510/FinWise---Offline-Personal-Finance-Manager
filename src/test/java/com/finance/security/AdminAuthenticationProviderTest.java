package com.finance.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAuthenticationProviderTest {
    private final AdminAuthenticationProvider provider =
            new AdminAuthenticationProvider("admin", "private-test-password", new BCryptPasswordEncoder());

    @Test
    void grantsOnlyAdminAuthorityForConfiguredCredentials() {
        var result = provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("admin", "private-test-password"));

        assertEquals("admin", result.getName());
        assertEquals("ROLE_ADMIN", result.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rejectsIncorrectAdminPassword() {
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong-password")));
    }

    @Test
    void leavesOtherUsernamesToTheRegularAuthenticationProvider() {
        assertNull(provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("regular-user", "password")));
    }
}
