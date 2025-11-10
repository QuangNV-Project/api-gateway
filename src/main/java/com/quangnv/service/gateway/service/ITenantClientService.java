package com.quangnv.service.gateway.service;

import reactor.core.publisher.Mono;

public interface ITenantClientService {
    Mono<Long> getTenantIdByDomain(String domain);
}
