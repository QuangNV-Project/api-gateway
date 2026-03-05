package com.quangnv.service.gateway.config;

import com.quangnv.service.gateway.data.CorsProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Slf4j
@Configuration
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CorsConfig {
    CorsProperties corsProperties;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Cấu hình CORS an toàn để tránh CSRF
        if (corsProperties.isAllowCredentials() &&
                corsProperties.getAllowedOrigins() != null &&
                !corsProperties.getAllowedOrigins().isEmpty()) {
            config.setAllowedOriginPatterns(corsProperties.getAllowedOrigins());
            config.setAllowCredentials(corsProperties.isAllowCredentials());
        } else {
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(false);
        }

        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setExposedHeaders(corsProperties.getExposedHeaders());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
