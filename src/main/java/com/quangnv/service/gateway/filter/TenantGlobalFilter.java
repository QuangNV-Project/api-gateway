package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.data.TenantDto;
import com.quangnv.service.gateway.exception.TenantException;
import com.quangnv.service.gateway.service.impl.TenantClientService;
import com.quangnv.service.utility_shared.util.JwtUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.core.Ordered;

import java.util.Objects;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TenantGlobalFilter implements GlobalFilter, Ordered {
    WebClient.Builder webClientBuilder;

    public TenantGlobalFilter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String domain = Objects.requireNonNull(exchange.getRequest().getHeaders().getHost()).getHostName();

        return getTenantIdByDomain(domain)
                .flatMap(tenantId -> {
                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header("X-Tenant-ID", tenantId.toString())
                            .build();
                    // Đi tiếp
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
                .onErrorResume(e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                    return exchange.getResponse().setComplete();
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Cacheable("tenants")
    public Mono<Long> getTenantIdByDomain(String domain) {
        log.info(">>> CACHE MISS. Calling Tenant Service for: " + domain);

        return webClientBuilder.build()
                .get()
                .uri("/api/v1/tenants/by-domain?domain=" + domain)
                .retrieve()
                .bodyToMono(TenantDto.class)
                .map(TenantDto::getTenantId)
                .onErrorResume(e -> {
                    return Mono.error(new TenantException(domain));
                });
    }
}
