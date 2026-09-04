package com.quangnv.service.gateway.filter;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Profile({"dev", "local"})
public class DevNoCacheGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore().getHeaderValue());
            exchange.getResponse().getHeaders().setPragma("no-cache");
            exchange.getResponse().getHeaders().setExpires(0);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
