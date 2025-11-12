package com.quangnv.service.gateway.config;

import com.quangnv.service.gateway.constant.RouteNameConstant;
import com.quangnv.service.utility_shared.constant.ServiceConstant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Configuration;
import com.quangnv.service.gateway.filter.AuthenticationFilter;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GatewayConfig {
    private AuthenticationFilter authFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routesBuilder = builder.routes();

        // Khai báo các route cho từng service
        createServiceRoutes(routesBuilder, RouteNameConstant.AUTH, ServiceConstant.ServiceName.AUTH_SERVICE);
        createServiceRoutes(routesBuilder, RouteNameConstant.TENANT, ServiceConstant.ServiceName.TENANT_SERVICE);
        createServiceRoutes(routesBuilder, RouteNameConstant.BLOG, ServiceConstant.ServiceName.BLOG_SERVICE);
        createServiceRoutes(routesBuilder, RouteNameConstant.PLATFORM, ServiceConstant.ServiceName.PLATFORM_SERVICE);
        createServiceRoutes(routesBuilder, RouteNameConstant.PAYMENT, ServiceConstant.ServiceName.PAYMENT_SERVICE);

        // Special routes
        routesBuilder.route("gateway-health", r -> r.path("/api/health/**")
                .uri("no://op"));
        return routesBuilder.build();
    }

    /**
     * Hàm helper để tạo 2 route (public/private) cho 1 service
     */
    private void createServiceRoutes(RouteLocatorBuilder.Builder builder,
                                     String routeName,
                                     ServiceConstant.ServiceName service) {

        String serviceUri = service.toLoadBalancedUri();

        // 1. Tạo route PUBLIC
        builder.route(routeName, r -> r.path(
                        "/api/" + routeName + "/public/**"
                )
                .filters(f -> f.stripPrefix(3))
                .uri(serviceUri));

        // 2. Tạo route PRIVATE
        builder.route(routeName + "-private", r -> r.path(
                        "/api/" + routeName + "/private/**"
                )
                .filters(applyAuthFilter()) // Áp dụng filter xác thực
                .uri(serviceUri));
    }

    /**
     * Áp dụng filter xác thực và strip prefix
     */
    private Function<GatewayFilterSpec, UriSpec> applyAuthFilter() {
        return f -> (UriSpec) f
                .stripPrefix(3)
                .filter(authFilter.apply(new AuthenticationFilter.Config()));
    }
}

