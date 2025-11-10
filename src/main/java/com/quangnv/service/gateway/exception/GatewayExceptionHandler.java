package com.quangnv.service.gateway.exception;

import com.quangnv.service.utility_shared.dto.ApiResponse;
import com.quangnv.service.utility_shared.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(TenantException.class)
    public ResponseEntity<ApiResponse<Object>> handleTenantException(TenantException ex) {
        log.error("Tenant exception: {}", ex.getMessage());
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(response);
    }
}