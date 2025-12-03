package com.quangnv.service.gateway.filter;


import com.quangnv.service.utility_shared.exception.NotFoundException;
import com.quangnv.service.utility_shared.exception.UnauthorizedException;
import lombok.AccessLevel;

import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import lombok.experimental.FieldDefaults;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import com.quangnv.service.gateway.data.UserDto;
import org.springframework.stereotype.Component;
import org.springframework.core.io.buffer.DataBuffer;
import com.quangnv.service.utility_shared.util.JwtUtil;
import org.springframework.web.server.ServerWebExchange;
import com.quangnv.service.gateway.data.PatValidationRequest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpResponse;
import com.quangnv.service.gateway.exception.PatValidationException;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {
    JwtUtil jwtUtil;
    WebClient.Builder webClientBuilder;

    public AuthenticationFilter(JwtUtil jwtUtil, WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Lấy header Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No Authorization header");
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return handleJwtAuth(exchange, chain, authHeader.substring(7));
            }

            if (authHeader != null && authHeader.startsWith("Token ")) {
                return handlePatAuth(exchange, chain, authHeader.substring(6));
            }

            return onError(exchange, "Invalid Authorization header format");
        };
    }

    /**
     * Xử lý xác thực JWT (Stateless, tự validate)
     */
    private Mono<Void> handleJwtAuth(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        if (Boolean.FALSE.equals(jwtUtil.validateToken(token))) {
            return onError(exchange, "Invalid JWT token");
        }

        String username = jwtUtil.extractUsername(token);
        Long userId = jwtUtil.extractUserId(token);
        Collection<String> roles = jwtUtil.extractRoles(token);
        String rolesString;
        if (roles == null || roles.isEmpty()) {
            return Mono.error(new UnauthorizedException("User has no roles"));
        } else {
            rolesString = String.join(",", roles);
        }
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId.toString())
                .header("X-User-Name", username)
                .header("X-User-Role", rolesString).build();
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * Xử lý xác thực PAT (Stateful, gọi sang auth-service)
     */
    private Mono<Void> handlePatAuth(ServerWebExchange exchange, GatewayFilterChain chain, String rawToken) {
        return validateToken(rawToken).flatMap(userDto -> {
            String rolesString;
            if (userDto.getRoles() == null || userDto.getRoles().isEmpty()) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User has no roles"));
            } else {
                rolesString = String.join(",", (CharSequence) userDto.getRoles());
            }
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userDto.getUserId())
                    .header("X-User-Name", userDto.getUserName())
                    .header("X-User-Role", rolesString).build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }).onErrorResume(e -> onError(exchange, "Invalid Personal Access Token"));
    }

    /**
     * Phương thức helper để trả về lỗi
     */
    private Mono<Void> onError(ServerWebExchange exchange, String err) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);

        DataBuffer buffer = response.bufferFactory().wrap(err.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Lớp Config, có thể để trống nếu không cần cấu hình gì thêm
     */
    public static class Config {
    }

    private Mono<UserDto> validateToken(String rawToken) {
        return webClientBuilder.build()
                .post()
                .uri("lb://auth-service/api/auth/validate-pat")
                .bodyValue(new PatValidationRequest(rawToken))
                .retrieve()
                .bodyToMono(UserDto.class)
                .onErrorResume(e -> Mono.error(new PatValidationException("Invalid Personal Access Token")));
    }
}
