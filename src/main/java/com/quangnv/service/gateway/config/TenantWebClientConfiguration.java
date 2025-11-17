package com.quangnv.service.gateway.config;

import com.quangnv.service.gateway.service.ServiceInstanceTenantLoadBalancer;
import com.quangnv.service.utility_shared.constant.ServiceConstant;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;

public class TenantWebClientConfiguration {
    private final DiscoveryClient discoveryClient;

    public TenantWebClientConfiguration(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Bean
    ServiceInstanceListSupplier serviceInstanceListSupplier() {
        return new ServiceInstanceTenantLoadBalancer(discoveryClient, ServiceConstant.ServiceName.TENANT_SERVICE.getService());
    }
}
