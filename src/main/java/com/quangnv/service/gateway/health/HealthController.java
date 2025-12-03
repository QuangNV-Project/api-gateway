package com.quangnv.service.gateway.health;

import com.quangnv.service.utility_shared.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/health")
public class HealthController {
    @GetMapping("/check")
    public ResponseEntity<?> healthcheck() {
        return ResponseEntity.ok(ApiResponse.success("Api gateway is ok"));
    }
}

