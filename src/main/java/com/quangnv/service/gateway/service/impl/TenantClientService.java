package com.quangnv.service.gateway.service.impl;

import com.quangnv.service.gateway.data.TenantDto;
import com.quangnv.service.gateway.exception.TenantException;
import com.quangnv.service.gateway.service.ITenantClientService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TenantClientService implements ITenantClientService {
    WebClient webClient;

    public TenantClientService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("lb://tenant-service").build();
    }

    @Override
    @Cacheable("tenants")
    public Mono<Long> getTenantIdByDomain(String domain) {
        log.info(">>> CACHE MISS. Calling Tenant Service for: " + domain);

        return this.webClient.get()
                .uri("/api/v1/tenants/by-domain?domain=" + domain)
                .retrieve()
                .bodyToMono(TenantDto.class)
                .map(TenantDto::getTenantId)
                .onErrorResume(e -> {
                    return Mono.error(new TenantException(domain));
                });
    }
}
