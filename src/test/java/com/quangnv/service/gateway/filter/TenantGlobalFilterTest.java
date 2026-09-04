package com.quangnv.service.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quangnv.service.gateway.data.TenantDto;
import com.quangnv.service.gateway.exception.TenantException;
import com.quangnv.service.gateway.service.TenantMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantGlobalFilterTest {
    private final TenantMetadataService tenantService = mock(TenantMetadataService.class);
    private final TenantGlobalFilter filter = new TenantGlobalFilter(tenantService, new ObjectMapper());

    @Test
    void successfulLookupForwardsAllInjectedTenantHeaders() {
        TenantDto tenant = new TenantDto(7L, "example.test", "Example", "BLOG", 9L);
        when(tenantService.findByCode("tenant.example")).thenReturn(Mono.just(tenant));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/api/blog/public/posts", "tenant.example");

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(next -> {
            var headers = next.getRequest().getHeaders();
            return "7".equals(headers.getFirst("X-Tenant-ID"))
                    && "example.test".equals(headers.getFirst("X-Domain-Name"))
                    && "BLOG".equals(headers.getFirst("X-Project-Type"))
                    && "9".equals(headers.getFirst("X-Project-ID"));
        }));
    }

    @Test
    void tenantFailureWritesSafeJsonAndDoesNotInvokeDownstream() {
        when(tenantService.findByCode("tenant.example")).thenReturn(Mono.error(new TenantException(
                TenantException.Kind.NOT_FOUND, HttpStatus.NOT_FOUND.value(), "upstream secret")));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = exchange("/api/blog/public/posts", "tenant.example");

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Tenant was not found")
                .doesNotContain("upstream secret");
        verifyNoInteractions(chain);
    }

    @Test
    void downstreamTenantExceptionIsNotConvertedByTenantFilter() {
        TenantDto tenant = new TenantDto(7L, "example.test", "Example", "BLOG", 9L);
        when(tenantService.findByCode("tenant.example")).thenReturn(Mono.just(tenant));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.error(new TenantException(
                TenantException.Kind.CLIENT_ERROR, HttpStatus.BAD_REQUEST.value(), "downstream")));
        MockServerWebExchange exchange = exchange("/api/blog/public/posts", "tenant.example");

        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(TenantException.class);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private MockServerWebExchange exchange(String path, String host) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).host(host).build());
    }
}
