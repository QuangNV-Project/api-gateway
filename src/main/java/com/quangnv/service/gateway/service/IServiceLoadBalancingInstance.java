package com.quangnv.service.gateway.service;

import org.springframework.cloud.client.ServiceInstance;
import reactor.core.publisher.Flux;

import java.util.List;

public interface IServiceLoadBalancingInstance {
    String getServiceId();
    Flux<List<ServiceInstance>> get();
}
