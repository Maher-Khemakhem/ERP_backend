package com.example.erp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Autorise un frontend séparé (ex. React/Vue en localhost:3000, ou
 * l'app Claude Artifacts) à appeler cette API depuis un navigateur.
 *
 * IMPORTANT : allowedOrigins est volontairement restreint à des URLs
 * de développement local pour l'instant. À restreindre au(x) domaine(s)
 * réel(s) du frontend avant tout déploiement en production — ne
 * JAMAIS utiliser "*" avec des credentials activés.
 */
@Configuration
class WebConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}