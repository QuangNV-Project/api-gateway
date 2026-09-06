package com.quangnv.service.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quangnv.service.gateway.constant.CommonConstant;
import com.quangnv.service.gateway.constant.LoggingConstant;
import com.quangnv.service.gateway.data.TenantDto;
import com.quangnv.service.gateway.exception.TenantException;
import com.quangnv.service.gateway.service.TenantMetadataService;
import com.quangnv.service.utility_shared.constant.HeaderConstants;
import com.quangnv.service.utility_shared.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TenantGlobalFilter implements GlobalFilter, Ordered {
    TenantMetadataService tenantMetadataService;
    ObjectMapper objectMapper;

    public TenantGlobalFilter(TenantMetadataService tenantMetadataService, ObjectMapper objectMapper) {
        this.tenantMetadataService = tenantMetadataService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isBypassed(path)) {
            return chain.filter(exchange);
        }

        String tenantCode = exchange.getRequest().getHeaders().getFirst("X-Tenant-Code");
        if (tenantCode == null && exchange.getRequest().getHeaders().getHost() != null) {
            tenantCode = exchange.getRequest().getHeaders().getHost().getHostName();
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            return writeTenantError(exchange, new TenantException(
                    TenantException.Kind.CLIENT_ERROR, HttpStatus.BAD_REQUEST.value(),
                    "Tenant could not be identified"));
        }

        return tenantMetadataService.findByCode(tenantCode)
                // Recover before invoking the downstream chain: its TenantException must remain visible
                // to the gateway's regular exception handling rather than be reported as a lookup failure.
                .onErrorResume(TenantException.class,
                        error -> writeTenantError(exchange, error).then(Mono.empty()))
                .flatMap(tenantDto -> {
                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header(HeaderConstants.TENANT_ID, tenantDto.getTenantId().toString())
                            .header(HeaderConstants.DOMAIN_NAME, tenantDto.getDomainName())
                            .header(HeaderConstants.PROJECT_TYPE, tenantDto.getProjectType())
                            .header(HeaderConstants.PROJECT_ID, tenantDto.getProjectId().toString())
                            .build();
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                });
    }

    private boolean isBypassed(String path) {
        return path.equals(CommonConstant.FIN_TRACK_API_KEY_PREFIX)
                || path.startsWith(CommonConstant.FIN_TRACK_API_KEY_PREFIX + "/")
                || path.equals("/api/health/check");
    }

    private Mono<Void> writeTenantError(ServerWebExchange exchange, TenantException error) {
        String requestId = exchange.getAttributeOrDefault(LoggingConstant.REQUEST_ID_ATTRIBUTE,
                UUID.randomUUID().toString());
        exchange.getAttributes().put(LoggingConstant.REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(LoggingConstant.REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(error.getStatus()));
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error(error.getSafeMessage(), error.getStatus()));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception serializationError) {
            log.error("[{}] Could not serialize tenant error", requestId, serializationError);
            byte[] fallback = "{\"message\":\"Tenant resolution failed\",\"status\":502}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(fallback)));
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
