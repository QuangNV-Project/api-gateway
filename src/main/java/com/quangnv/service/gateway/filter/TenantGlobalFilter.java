package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.data.TenantDto;
import com.quangnv.service.gateway.exception.TenantException;
import com.quangnv.service.utility_shared.constant.HeaderConstants;
import com.quangnv.service.utility_shared.constant.ServiceConstant;
import com.quangnv.service.utility_shared.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.ParameterizedTypeReference;
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
        String domain = exchange.getRequest().getHeaders().getFirst("X-HOST-NAME");
        if (domain == null) {
            domain = Objects.requireNonNull(exchange.getRequest().getHeaders().getHost()).getHostName();
        }

        return getTenantIdByDomain(domain)
                .flatMap(tenantDto -> {
                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header(HeaderConstants.TENANT_ID, tenantDto.getTenantId().toString())
                            .header(HeaderConstants.DOMAIN_NAME, tenantDto.getDomainName())
                            .header(HeaderConstants.PROJECT_TYPE, tenantDto.getProjectType())
                            .header(HeaderConstants.PROJECT_ID, tenantDto.getProjectId().toString())
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
    public Mono<TenantDto> getTenantIdByDomain(String domain) {
        log.info(">>> CACHE MISS. Calling Tenant Service for: {}", domain);
        return webClientBuilder
                .baseUrl("http://" + ServiceConstant.ServiceName.TENANT_SERVICE.getService())
                .build()
                .get()
                .uri("/tenant/by-domain?domain={domain}", domain)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<TenantDto>>() {
                })
                .flatMap(apiResponse -> {
                    if (apiResponse.getData() == null || apiResponse.getData().getTenantId() == null) {
                        return Mono.error(new TenantException(domain));
                    }
                    return Mono.just(apiResponse.getData());
                })
                .onErrorResume(e -> {
                    log.error(">>> CACHE MISS. Fetched Tenant ID Failed: {}", e.getMessage());
                    return Mono.error(new TenantException(domain));
                });
    }
}
