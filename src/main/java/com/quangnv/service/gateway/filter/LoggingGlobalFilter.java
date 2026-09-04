package com.quangnv.service.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quangnv.service.gateway.constant.LoggingConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {
    private static final String REQUEST_ID_PATTERN = "[A-Za-z0-9._:-]{1,128}";
    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "authorization", "cookie", "set-cookie", "token", "access-token", "refresh-token",
            "api-key", "apikey", "x-api-key", "password", "passwd", "secret", "client-secret",
            "credential", "credentials", "session", "session-id"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean developmentLogging;
    private final int maxBodyLogBytes;

    @Autowired
    public LoggingGlobalFilter(Environment environment,
                               @Value("${gateway.logging.max-body-log-bytes:" + LoggingConstant.MAX_BODY_LOG_BYTES + "}") int maxBodyLogBytes) {
        this.developmentLogging = isDevelopmentProfile(environment);
        this.maxBodyLogBytes = Math.max(0, maxBodyLogBytes);
    }

    public LoggingGlobalFilter() {
        this.developmentLogging = false;
        this.maxBodyLogBytes = LoggingConstant.MAX_BODY_LOG_BYTES;
    }

    public LoggingGlobalFilter(boolean developmentLogging, int maxBodyLogBytes) {
        this.developmentLogging = developmentLogging;
        this.maxBodyLogBytes = Math.max(0, maxBodyLogBytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String suppliedRequestId = request.getHeaders().getFirst(LoggingConstant.REQUEST_ID_HEADER);
        String requestId = suppliedRequestId != null && suppliedRequestId.matches(REQUEST_ID_PATTERN)
                ? suppliedRequestId : UUID.randomUUID().toString();

        exchange.getAttributes().put(LoggingConstant.REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getResponse().getHeaders().set(LoggingConstant.REQUEST_ID_HEADER, requestId);
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(LoggingConstant.REQUEST_ID_HEADER, requestId)
                .build();

        ServerWebExchange forwardedExchange = exchange.mutate().request(mutatedRequest).build();
        if (!developmentLogging) {
            log.info("[{}] >>> Request: {} {}", requestId, request.getMethod(), request.getPath().value());
        } else if (request.getHeaders().getContentLength() == 0) {
            log.info("[{}] >>> Request: {}", requestId, buildCurl(request, "", false, 0));
        } else {
            Capture capture = new Capture(request, requestId);
            ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(mutatedRequest) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return super.getBody()
                            .doOnNext(capture::accept)
                            .doFinally(signal -> capture.complete());
                }
            };
            forwardedExchange = exchange.mutate().request(decoratedRequest).build();
        }

        ServerWebExchange finalExchange = forwardedExchange;
        return chain.filter(finalExchange)
                .doFinally(signal -> log.info("[{}] <<< Response: status={}",
                        requestId, exchange.getResponse().getStatusCode()));
    }

    private static boolean isDevelopmentProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("local".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    String buildCurl(ServerHttpRequest request, String body, boolean omitted, long bodyLength) {
        StringBuilder curl = new StringBuilder("curl --request ")
                .append(shellQuote(String.valueOf(request.getMethod())))
                .append(" --url ")
                .append(shellQuote(maskQuery(request.getURI().toString())));
        request.getHeaders().forEach((name, values) -> {
            String value = isSensitive(name) ? LoggingConstant.MASKED_HEADER_VALUE : String.join(",", values);
            curl.append(" --header ").append(shellQuote(name + ": " + value));
        });
        if (omitted) {
            curl.append(" --data-raw ").append(shellQuote("<body omitted: length=" + bodyLength + " bytes>"));
        } else if (!body.isEmpty()) {
            curl.append(" --data-raw ").append(shellQuote(maskJsonIfNeeded(request.getHeaders().getContentType(), body)));
        }
        return curl.toString();
    }

    private String maskQuery(String uri) {
        int queryStart = uri.indexOf('?');
        if (queryStart < 0) return uri;
        int fragmentStart = uri.indexOf('#', queryStart);
        String query = uri.substring(queryStart + 1, fragmentStart < 0 ? uri.length() : fragmentStart);
        String[] parts = query.split("&", -1);
        for (int i = 0; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            String name = equals < 0 ? parts[i] : parts[i].substring(0, equals);
            if (isSensitive(name)) {
                parts[i] = name + (equals < 0 ? "=" + MASK : "=" + MASK);
            }
        }
        String masked = String.join("&", parts);
        return uri.substring(0, queryStart + 1) + masked + (fragmentStart < 0 ? "" : uri.substring(fragmentStart));
    }

    private static String maskJsonIfNeeded(MediaType contentType, String body) {
        if (contentType == null || !isJson(contentType)) return body;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            redactJson(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            return body;
        }
    }

    private static void redactJson(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            node.fieldNames().forEachRemaining(name -> {
                JsonNode child = node.get(name);
                if (isSensitive(name)) object.put(name, MASK);
                else redactJson(child);
            });
        } else if (node.isArray()) {
            node.forEach(LoggingGlobalFilter::redactJson);
        }
    }

    private static boolean isJson(MediaType type) {
        return type.isCompatibleWith(MediaType.APPLICATION_JSON) || type.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json");
    }

    private static boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim();
        return SENSITIVE_NAMES.contains(normalized) || SENSITIVE_NAMES.stream().anyMatch(normalized::contains);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private final class Capture {
        private final ServerHttpRequest request;
        private final String requestId;
        private final byte[] bytes = new byte[maxBodyLogBytes];
        private int captured;
        private long length;
        private boolean binary;
        private final AtomicBoolean logged = new AtomicBoolean();

        private Capture(ServerHttpRequest request, String requestId) {
            this.request = request;
            this.requestId = requestId;
        }

        private void accept(DataBuffer buffer) {
            int readable = buffer.readableByteCount();
            length += readable;
            ByteBuffer source = buffer.toByteBuffer(buffer.readPosition(), readable);
            int copy = Math.min(readable, maxBodyLogBytes - captured);
            source.get(bytes, captured, copy);
            captured += copy;
            if (request.getHeaders().getContentType() == null || !isTextual(request.getHeaders().getContentType())) {
                binary = true;
            }
        }

        private void complete() {
            if (!logged.compareAndSet(false, true)) return;
            String body = new String(bytes, 0, captured, StandardCharsets.UTF_8);
            boolean omitted = length > maxBodyLogBytes || binary
                    || (isJson(request.getHeaders().getContentType()) && !isParsableJson(body));
            log.info("[{}] >>> Request: {}", requestId, buildCurl(request, omitted ? "" : body, omitted, length));
        }
    }

    private static boolean isParsableJson(String body) {
        try {
            OBJECT_MAPPER.readTree(body);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isTextual(MediaType type) {
        return type.getType().equalsIgnoreCase("text") || isJson(type)
                || List.of("xml", "x-www-form-urlencoded", "javascript").contains(type.getSubtype().toLowerCase(Locale.ROOT));
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
