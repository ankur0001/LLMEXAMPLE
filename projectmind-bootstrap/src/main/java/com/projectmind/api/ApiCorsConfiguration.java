package com.projectmind.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for browser clients calling the REST API.
 */
@Configuration
public class ApiCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public ApiCorsConfiguration(
            @Value("${projectmind.api.cors-allowed-origins:http://localhost:3000,http://localhost:8080}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
