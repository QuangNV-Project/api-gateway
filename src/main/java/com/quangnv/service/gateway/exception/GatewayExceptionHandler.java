package com.quangnv.service.gateway.exception;

import com.quangnv.service.gateway.constant.LoggingConstant;
import com.quangnv.service.utility_shared.dto.ApiResponse;
import com.quangnv.service.utility_shared.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex, ServerWebExchange exchange) {
        String requestId = getRequestId(exchange);
        log.error("[{}] Unhandled exception: {} - {}", requestId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        ApiResponse<Object> response = ApiResponse.error("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(TenantException.class)
    public ResponseEntity<ApiResponse<Object>> handleTenantException(TenantException ex, ServerWebExchange exchange) {
        String requestId = getRequestId(exchange);
        log.error("[{}] Tenant exception: {}", requestId, ex.getMessage());
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(PatValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handlePatValidationException(PatValidationException ex, ServerWebExchange exchange) {
        String requestId = getRequestId(exchange);
        log.error("[{}] PatValidationException: {}", requestId, ex.getMessage());
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.badRequest().body(response);
    }

    private static String getRequestId(ServerWebExchange exchange) {
        if (exchange == null) return "-";
        String id = exchange.getAttribute(LoggingConstant.REQUEST_ID_ATTRIBUTE);
        return id != null ? id : "-";
    }
}