package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.constant.CommonConstant;
import com.quangnv.service.gateway.data.ApiKeyProperties;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApiKeyAuthFilter implements GatewayFilter {
    ApiKeyProperties apiKeyProperties;

    public ApiKeyAuthFilter(ApiKeyProperties apiKeyProperties) {
        this.apiKeyProperties = apiKeyProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String configuredKey = apiKeyProperties.getFinTrack();
        if (!StringUtils.hasText(configuredKey)) {
            log.error("gateway.api-key.fin-track is not configured");
            return onError(exchange, "API key is not configured");
        }

        String requestKey = exchange.getRequest().getHeaders().getFirst(CommonConstant.API_KEY_HEADER);
        if (!StringUtils.hasText(requestKey) || !configuredKey.equals(requestKey)) {
            return onError(exchange, "Invalid API key");
        }

        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        DataBuffer buffer = response.bufferFactory().wrap(err.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}


