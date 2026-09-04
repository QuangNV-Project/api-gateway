package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.constant.LoggingConstant;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoggingAndNoCacheFilterTest {
    @Test
    void invalidRequestIdIsReplacedAndForwarded() {
        LoggingGlobalFilter filter = new LoggingGlobalFilter();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/health/check")
                .header(LoggingConstant.REQUEST_ID_HEADER, "bad value")
                .build());

        filter.filter(exchange, chain).block();

        String requestId = exchange.getAttribute(LoggingConstant.REQUEST_ID_ATTRIBUTE);
        assertThat(requestId).isNotEqualTo("bad value").matches("[A-Za-z0-9._:-]{1,128}");
        assertThat(exchange.getResponse().getHeaders().getFirst(LoggingConstant.REQUEST_ID_HEADER)).isEqualTo(requestId);
        verify(chain).filter(argThat(next -> requestId.equals(
                next.getRequest().getHeaders().getFirst(LoggingConstant.REQUEST_ID_HEADER))));
    }

    @Test
    void devNoCacheFilterAddsOnlyDevelopmentHeaders() {
        DevNoCacheGlobalFilter filter = new DevNoCacheGlobalFilter();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange forwarded = invocation.getArgument(0);
            forwarded.getResponse().getHeaders().setCacheControl("public, max-age=3600");
            return forwarded.getResponse().setComplete();
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/health/check").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(exchange.getResponse().getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(exchange.getResponse().getHeaders().getFirst("Expires")).isEqualTo("0");
        verify(chain).filter(exchange);
    }

    @Test
    void loggingDoesNotConsumeRequestBodyOrExposeQueryInForwardedRequest() {
        LoggingGlobalFilter filter = new LoggingGlobalFilter();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange forwarded = invocation.getArgument(0);
            return forwarded.getRequest().getBody()
                    .reduce(new StringBuilder(), (builder, buffer) -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return builder.append(new String(bytes, StandardCharsets.UTF_8));
                    })
                    .doOnNext(body -> assertThat(body.toString()).isEqualTo("secret-body"))
                    .then();
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(
                        "/api/items?token=must-not-be-logged")
                .body("secret-body"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(forwarded -> forwarded.getRequest().getURI().getRawQuery()
                .equals("token=must-not-be-logged")));
    }

    @Test
    void devProfileForwardsOriginalBodyAndKeepsOriginalQuery() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        LoggingGlobalFilter filter = new LoggingGlobalFilter(environment, 4096);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange forwarded = invocation.getArgument(0);
            return forwarded.getRequest().getBody()
                    .reduce(new StringBuilder(), (builder, buffer) -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return builder.append(new String(bytes, StandardCharsets.UTF_8));
                    })
                    .doOnNext(body -> assertThat(body.toString()).isEqualTo("{\"name\":\"ok\",\"password\":\"secret\"}"))
                    .then();
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(
                        "/api/items?query=keep&access_token=secret-value")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"ok\",\"password\":\"secret\"}"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(forwarded -> forwarded.getRequest().getURI().getRawQuery()
                .equals("query=keep&access_token=secret-value")));
    }

    @Test
    void curlMasksSensitiveValuesAndEscapesShellCharacters() {
        LoggingGlobalFilter filter = new LoggingGlobalFilter(true, 4096);
        MockServerHttpRequest request = MockServerHttpRequest.post("/items?keep=a%20b&token=raw-secret")
                .header("Authorization", "Bearer raw-secret")
                .header("X-Custom", "it's safe")
                .contentType(MediaType.APPLICATION_JSON)
                .build();

        String curl = filter.buildCurl(request, "{\"nested\":{\"API_KEY\":\"raw-secret\"},\"ok\":\"it's safe\"}", false, 0);

        assertThat(curl).contains("token=***").contains("Authorization: ***")
                .contains("X-Custom: it'\\''s safe").contains("API_KEY\\\":\\\"***")
                .doesNotContain("raw-secret");
        assertThat(request.getURI().getRawQuery()).isEqualTo("keep=a%20b&token=raw-secret");
    }

    @Test
    void curlUsesBoundedOmissionMarkerForOversizedBody() {
        LoggingGlobalFilter filter = new LoggingGlobalFilter(true, 4);
        MockServerHttpRequest request = MockServerHttpRequest.post("/items").build();

        String curl = filter.buildCurl(request, "", true, 12);

        assertThat(curl).contains("<body omitted: length=12 bytes>").doesNotContain("--data-raw ''");
    }

    @Test
    void prodProfileDoesNotSubscribeToRequestBody() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        LoggingGlobalFilter filter = new LoggingGlobalFilter(environment, 4096);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        AtomicInteger subscriptions = new AtomicInteger();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/items")
                .body(Flux.defer(() -> {
                    subscriptions.incrementAndGet();
                    return Flux.just(new DefaultDataBufferFactory().wrap("body-that-must-not-be-read".getBytes(StandardCharsets.UTF_8)));
                })));

        filter.filter(exchange, chain).block();

        assertThat(subscriptions).hasValue(0);
        verify(chain).filter(any());
    }
}
