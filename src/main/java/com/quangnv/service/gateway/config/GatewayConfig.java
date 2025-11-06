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
    private AuthenticationFilter authFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                // Auth Service - Public endpoints
                .route("auth-login", r -> r.path(
                                "/api/auth/**"
                        )
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://auth-service"))


                // Platform Service
                .route("platform-service", r -> r.path("/api/platform/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://platform-service"))
                .route("product-protected", r -> r.path("/api/products/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://product-service"))

                // Order Service
                .route("order-service", r -> r.path("/api/orders/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://order-service"))

                // Payment Service
                .route("payment-service", r -> r.path("/api/payments/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://payment-service"))

                // Notification Service
                .route("notification-service", r -> r.path("/api/notifications/**")
                        .filters(f -> f.filter(authFilter))
                        .uri("lb://notification-service"))
                .route("gateway-health", r -> r.path("/api/health/**")
                        .uri("no://op")) // không forward đi đâu cả

                .build();
    }
}

