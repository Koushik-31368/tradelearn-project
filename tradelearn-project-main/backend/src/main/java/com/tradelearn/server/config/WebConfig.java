package com.tradelearn.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    // Comma-separated list of allowed origins (set in application.properties or env)
    @Value("${app.cors.origins:http://localhost:3000}")
    private String corsOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        final String[] origins = parseOrigins(corsOrigins);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    private String[] parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) return new String[] {"http://localhost:3000"};
        return raw.split("\\s*,\\s*");
    }
}
