package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.service.impl.TenantClientService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.core.Ordered;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TenantGlobalFilter implements GlobalFilter, Ordered {
    TenantClientService tenantService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String domain = Objects.requireNonNull(exchange.getRequest().getHeaders().getHost()).getHostName();

        return tenantService.getTenantIdByDomain(domain)
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
}
