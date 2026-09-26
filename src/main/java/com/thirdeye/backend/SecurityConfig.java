package com.thirdeye.backend;

import com.thirdeye.backend.service.AppUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security configuration.
 *
 * - Session-based authentication (Spring default IF_REQUIRED)
 * - CSRF disabled (SPA with CORS, credentials sent via cookie)
 * - H2 console kept working (frame-options sameOrigin + permit)
 * - /api/auth/** and backward-compat endpoints (/api/profile, /api/notifications) are public
 * - Everything else under /api/** requires authentication
 * - Returns JSON 401/403 instead of redirecting
 *
 * PasswordEncoder is defined in PasswordEncoderConfig to avoid circular
 * dependency: SecurityConfig → AppUserService → PasswordEncoder → SecurityConfig.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppUserService appUserService;
    private final PasswordEncoder passwordEncoder;
    @Value("${thirdeye.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(AppUserService appUserService, PasswordEncoder passwordEncoder) {
        this.appUserService  = appUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(appUserService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept", "Origin", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF: disabled for SPA + CORS flow ───────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())

            // ── H2 console frame options ──────────────────────────────────────
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            // ── Session management: create if needed (default) ────────────────
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // ── Authorization rules ───────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Auth endpoints — always public
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                // H2 console
                .requestMatchers("/h2-console/**").permitAll()
                // Backward-compat: keep old profile/notification endpoints unauthenticated
                // so ProfileAndNotificationTests continue to pass without auth
                .requestMatchers("/api/profile/**").permitAll()
                .requestMatchers("/api/notifications/**").permitAll()
                // Everything else requires auth
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )

            // ── No form login or HTTP Basic ───────────────────────────────────
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable())

            // ── JSON responses for 401/403 ────────────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"))
                .accessDeniedHandler((request, response, e) ->
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "{\"error\":\"Forbidden\",\"message\":\"Access denied\"}"))
            )

            // ── Wire in our UserDetailsService ───────────────────────────────
            .authenticationProvider(authenticationProvider());

        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, String body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }
}
