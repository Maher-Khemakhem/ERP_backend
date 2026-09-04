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
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:[*]",
                "https://localhost:[*]",
                "http://127.0.0.1:[*]",
                "https://127.0.0.1:[*]",
                "http://192.168.*:[*]",
                "https://192.168.*:[*]",
                "http://10.*:[*]",
                "https://10.*:[*]",
                "http://172.16.*:[*]",
                "https://172.16.*:[*]"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
