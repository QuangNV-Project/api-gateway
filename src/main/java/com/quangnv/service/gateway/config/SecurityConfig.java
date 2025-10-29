package com.quangnv.service.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF protection vì đây là API Gateway dùng JWT
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Disable form login và basic auth vì dùng JWT
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // Authorize all requests (authentication được handle bởi AuthenticationFilter)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll())

                .build();
    }
}
