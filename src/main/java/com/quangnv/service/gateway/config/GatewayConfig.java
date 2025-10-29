package com.quangnv.service.gateway.config;

import com.quangnv.service.gateway.filter.AuthenticationFilter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GatewayConfig {
    AuthenticationFilter authFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth Service - Public endpoints
                .route("auth-login", r -> r.path("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/oauth2/**")
                        .uri("lb://auth-service"))
                
                // Auth Service - Protected endpoints
                .route("auth-protected", r -> r.path("/api/auth/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://auth-service"))
                
                // Product Service - Public endpoints (viewing products)
                .route("product-public", r -> r.path("/api/products/public/**")
                        .uri("lb://product-service"))
                
                // Product Service - Protected endpoints
                .route("product-protected", r -> r.path("/api/products/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://product-service"))
                
                // Order Service - All protected
                .route("order-service", r -> r.path("/api/orders/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://order-service"))
                
                // Payment Service - All protected
                .route("payment-service", r -> r.path("/api/payments/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://payment-service"))
                
                // Notification Service - Protected
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://notification-service"))
                .build();
    }
}
