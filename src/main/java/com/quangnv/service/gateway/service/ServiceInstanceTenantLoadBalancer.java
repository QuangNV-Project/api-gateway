package com.quangnv.service.gateway.service;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

import java.util.List;

public class ServiceInstanceTenantLoadBalancer implements ServiceInstanceListSupplier {
    private final DiscoveryClient discoveryClient;
    private final String serviceId;

    public ServiceInstanceTenantLoadBalancer(DiscoveryClient discoveryClient, String serviceId) {
        this.discoveryClient = discoveryClient;
        this.serviceId = serviceId;
    }


    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        // Fetch the service instances from the DiscoveryClient
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        return Flux.just(instances);
    }
}
