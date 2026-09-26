package com.thirdeye.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Centralised CORS configuration for all /api/** endpoints.
 *
 * Reads the comma-separated list of allowed origins from
 * thirdeye.cors.allowed-origins (application.properties) so the
 * four controllers no longer need individual @CrossOrigin annotations.
 *
 * Wildcard origins are intentionally NOT used because the frontend
 * sends no credentials; however, the explicit origin list keeps
 * the configuration forward-compatible if credentials are added later.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** Injected as a List — Spring splits the comma-separated property automatically. */
    @Value("${thirdeye.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
