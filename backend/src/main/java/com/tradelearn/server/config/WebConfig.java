package com.tradelearn.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // You can keep this file for other MVC settings like Interceptors,
    // but do NOT put CORS logic here if it's already in SecurityConfig.
}