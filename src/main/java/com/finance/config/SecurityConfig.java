package com.finance.config;

import com.finance.security.AdminAuthenticationProvider;
import com.finance.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsServiceImpl userDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           DaoAuthenticationProvider authenticationProvider,
                                           AdminAuthenticationProvider adminAuthenticationProvider) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        http
                .authenticationProvider(adminAuthenticationProvider)
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize ->
                        authorize
                                 .requestMatchers("/", "/register", "/auth/register", "/login", "/reset-password", "/service-worker.js", "/manifest.webmanifest", "/icons/**", "/css/**", "/js/**", "/static/**", "/vendor/**", "/webjars/**", "/locales/**", "/uploads/**").permitAll()
                                 .requestMatchers("/admin/**").hasRole("ADMIN")
                                 .requestMatchers("/api/chatbot/help").permitAll()
                                 .requestMatchers("/api/chatbot/**", "/chatbot").authenticated()
                                 .anyRequest().authenticated()
                )
                .formLogin(login ->
                        login
                                .loginPage("/login")
                                .successHandler((request, response, authentication) -> {
                                    boolean isAdmin = authentication.getAuthorities().stream()
                                            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                                    response.sendRedirect(request.getContextPath() + (isAdmin ? "/admin" : "/dashboard"));
                                })
                                .permitAll()
                )
                .logout(logout ->
                        logout
                                .logoutUrl("/logout")
                                .logoutSuccessUrl("/login?logout")
                                .permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                )
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository));

        return http.build();
    }
}
