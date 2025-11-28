package com.quangnv.service.gateway.webclient;

import com.quangnv.service.gateway.config.TenantWebClientConfiguration;
import com.quangnv.service.utility_shared.constant.ServiceConstant;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@LoadBalancerClient(name = "tenant-service", configuration = TenantWebClientConfiguration.class)
public class WebClientTenantService {
    @Bean
    @LoadBalanced
    public WebClient.Builder roleAuthorityServiceWebClientBuilder() {
        return WebClient.builder().baseUrl("http://" + ServiceConstant.ServiceName.TENANT_SERVICE.getService());
    }
}
