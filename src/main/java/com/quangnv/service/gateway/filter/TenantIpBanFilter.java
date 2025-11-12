package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.constant.RedisConstant;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TenantIpBanFilter implements GlobalFilter, Ordered {

    ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
        if (tenantId == null) {
            return chain.filter(exchange);
        }

        //Lấy IP
        String sourceIp = extractClientIp(exchange);
        if (sourceIp == null) {
            return chain.filter(exchange);
        }

        String cacheKey = RedisConstant.TENANT_PREFIX + tenantId + RedisConstant.IP_BAN_SUFFIX;

        //Tra cứu Redis
        return reactiveRedisTemplate.opsForSet().isMember(cacheKey, sourceIp)
                .flatMap(isBanned -> {
                    if (Boolean.TRUE.equals(isBanned)) {
                        // 5. Bị ban -> Trả về lỗi 403. DỪNG LẠI.
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    } else {
                        // 6. Không bị ban -> Cho đi tiếp (sang AuthenticationFilter)
                        return chain.filter(exchange);
                    }
                });
    }

    @Override
    public int getOrder() {
        return -50;
    }

    private String extractClientIp(ServerWebExchange exchange) {
        // (logic lấy IP như cũ)
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip != null) {
            return ip;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return null;
    }
}