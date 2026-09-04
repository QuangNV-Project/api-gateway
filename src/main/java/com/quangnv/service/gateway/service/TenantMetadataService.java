package com.quangnv.service.gateway.service;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.quangnv.service.gateway.data.TenantDto;
import com.quangnv.service.gateway.exception.TenantException;
import com.quangnv.service.utility_shared.constant.ServiceConstant;
import com.quangnv.service.utility_shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TenantMetadataService {
    private final WebClient.Builder webClientBuilder;
    private final AsyncLoadingCache<String, TenantDto> cache;

    public TenantMetadataService(
            WebClient.Builder webClientBuilder,
            @Value("${gateway.tenant-cache.maximum-size:1000}") long maximumSize,
            @Value("${gateway.tenant-cache.ttl:10m}") Duration ttl) {
        this.webClientBuilder = webClientBuilder;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .buildAsync((tenantCode, executor) -> fetchTenant(tenantCode));
    }

    public Mono<TenantDto> findByCode(String tenantCode) {
        return Mono.defer(() -> Mono.fromFuture(cache.get(tenantCode)))
                .doOnError(error -> cache.synchronous().invalidate(tenantCode));
    }

    private CompletableFuture<TenantDto> fetchTenant(String tenantCode) {
        return webClientBuilder
                .baseUrl("http://" + ServiceConstant.ServiceName.TENANT_SERVICE.getService())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder.path("/tenant/by-code").queryParam("code", tenantCode).build())
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new TenantException(
                                TenantException.Kind.NOT_FOUND, HttpStatus.NOT_FOUND.value(),
                                "Tenant was not found")))
                .onStatus(status -> status.is4xxClientError(),
                        response -> Mono.error(new TenantException(
                                TenantException.Kind.CLIENT_ERROR, HttpStatus.BAD_REQUEST.value(),
                                "Tenant request was rejected")))
                .onStatus(status -> status.is5xxServerError(),
                        response -> Mono.error(new TenantException(
                                TenantException.Kind.UPSTREAM_ERROR, HttpStatus.BAD_GATEWAY.value(),
                                "Tenant service is unavailable")))
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<TenantDto>>() {})
                .switchIfEmpty(Mono.error(new TenantException(
                        TenantException.Kind.MALFORMED_RESPONSE, HttpStatus.BAD_GATEWAY.value(),
                        "Tenant service returned an invalid response")))
                .flatMap(response -> {
                    TenantDto tenant = response.getData();
                    if (tenant == null || tenant.getTenantId() == null || tenant.getProjectId() == null
                            || tenant.getDomainName() == null || tenant.getProjectType() == null) {
                        return Mono.error(new TenantException(
                                TenantException.Kind.MALFORMED_RESPONSE, HttpStatus.BAD_GATEWAY.value(),
                                "Tenant service returned an invalid response"));
                    }
                    return Mono.just(tenant);
                })
                .onErrorMap(error -> {
                    if (error instanceof TenantException) {
                        return error;
                    }
                    log.warn("Tenant service lookup failed for transport error type {}", error.getClass().getSimpleName());
                    return new TenantException(
                            TenantException.Kind.TRANSPORT_ERROR, HttpStatus.BAD_GATEWAY.value(),
                            "Tenant service is unavailable", error);
                })
                .toFuture();
    }
}
