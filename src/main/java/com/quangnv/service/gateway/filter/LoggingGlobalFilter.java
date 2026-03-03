package com.quangnv.service.gateway.filter;

import com.quangnv.service.gateway.constant.LoggingConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION = "authorization";
    private static final List<String> MASKED_HEADERS = List.of(AUTHORIZATION, "token", "cookie");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = Optional.ofNullable(request.getHeaders().getFirst(LoggingConstant.REQUEST_ID_HEADER))
                .orElse(UUID.randomUUID().toString());

        exchange.getAttributes().put(LoggingConstant.REQUEST_ID_ATTRIBUTE, requestId);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(LoggingConstant.REQUEST_ID_HEADER, requestId)
                .build();

        // Log request (method, path, query, headers)
        logRequest(requestId, mutatedRequest);

        // Nếu có body và là JSON/text thì cache toàn bộ, log (có thể truncate), rồi replay
        if (hasBody(mutatedRequest) && isLoggableContentType(mutatedRequest.getHeaders().getContentType())) {
            return DataBufferUtils.join(mutatedRequest.getBody())
                    .flatMap(dataBuffer -> {
                        int total = dataBuffer.readableByteCount();
                        byte[] fullBytes = new byte[total];
                        dataBuffer.read(fullBytes);
                        DataBufferUtils.release(dataBuffer);
                        int logLen = Math.min(total, LoggingConstant.MAX_BODY_LOG_BYTES);
                        String bodyPreview = new String(fullBytes, 0, logLen, StandardCharsets.UTF_8);
                        if (total > LoggingConstant.MAX_BODY_LOG_BYTES) bodyPreview += "... [truncated]";
                        log.info("[{}] Request body: {}", requestId, maskSensitiveInJson(bodyPreview));

                        ServerHttpRequestDecorator replayRequest = new ServerHttpRequestDecorator(mutatedRequest) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(exchange.getResponse().bufferFactory().wrap(fullBytes));
                            }
                        };
                        return chain.filter(exchange.mutate().request(replayRequest).response(wrapResponse(exchange, requestId)).build());
                    })
                    .switchIfEmpty(chain.filter(exchange.mutate().request(mutatedRequest).response(wrapResponse(exchange, requestId)).build()));
        }

        ServerHttpResponseDecorator responseDecorator = wrapResponse(exchange, requestId);
        return chain.filter(exchange.mutate().request(mutatedRequest).response(responseDecorator).build());
    }

    private void logRequest(String requestId, ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String query = request.getURI().getQuery() != null ? "?" + request.getURI().getQuery() : "";
        String headers = request.getHeaders().entrySet().stream()
                .map(e -> e.getKey() + "=" + maskHeader(e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
        log.info("[{}] >>> Request: {} {}{} | Headers: {}", requestId, method, path, query, headers);
    }

    private String maskHeader(String name, List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        if (MASKED_HEADERS.stream().anyMatch(h -> name.equalsIgnoreCase(h))) {
            return LoggingConstant.MASKED_HEADER_VALUE;
        }
        return String.join(",", values);
    }

    private boolean hasBody(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        long contentLength = headers.getContentLength();
        return contentLength > 0 || (contentLength == -1 && headers.get(HttpHeaders.TRANSFER_ENCODING) != null);
    }

    private boolean isLoggableContentType(MediaType type) {
        if (type == null) return false;
        return type.includes(MediaType.APPLICATION_JSON) || type.includes(MediaType.TEXT_PLAIN)
                || type.getType().equals("application") && "json".equalsIgnoreCase(type.getSubtype());
    }

    private ServerHttpResponseDecorator wrapResponse(ServerWebExchange exchange, String requestId) {
        ServerHttpResponse response = exchange.getResponse();
        return new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(Flux<? extends DataBuffer> body) {
                return DataBufferUtils.join(body)
                        .flatMap(dataBuffer -> {
                            int total = dataBuffer.readableByteCount();
                            byte[] full = new byte[total];
                            dataBuffer.read(full);
                            DataBufferUtils.release(dataBuffer);

                            int logLen = Math.min(total, LoggingConstant.MAX_BODY_LOG_BYTES);
                            String preview = new String(full, 0, logLen, StandardCharsets.UTF_8);
                            if (total > LoggingConstant.MAX_BODY_LOG_BYTES) preview += "... [truncated]";
                            HttpStatusCode status = getStatusCode();
                            log.info("[{}] <<< Response: status={} | body: {}", requestId, status != null ? status : "(unknown)", maskSensitiveInJson(preview));

                            return getDelegate().writeWith(Flux.just(response.bufferFactory().wrap(full)));
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            log.info("[{}] <<< Response: status={} | body: (empty)", requestId, getStatusCode());
                            return getDelegate().writeWith(Flux.empty());
                        }));
            }
        };
    }

    /** Đơn giản mask giá trị nhạy cảm trong JSON (password, token, ...). */
    private String maskSensitiveInJson(String json) {
        if (json == null || json.isEmpty()) return json;
        return json.replaceAll("(\"(?:password|token|accessToken|refreshToken|secret)\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
