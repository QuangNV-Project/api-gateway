package com.quangnv.service.gateway.service.impl;

import com.quangnv.service.gateway.data.PatValidationRequest;
import com.quangnv.service.gateway.data.UserDto;
import com.quangnv.service.gateway.exception.PatValidationException;
import com.quangnv.service.gateway.service.IAuthService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthService implements IAuthService {
    WebClient webClient;

    public AuthService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("lb://auth-service").build();
    }

    @Override
    public Mono<UserDto> validateToken(String rawToken) {
        return this.webClient
                .post()
                .uri("lb://auth-service/api/auth/validate-pat")
                .bodyValue(new PatValidationRequest(rawToken))
                .retrieve()
                .bodyToMono(UserDto.class)
                .onErrorResume(e -> Mono.error(new PatValidationException("Invalid Personal Access Token")));
    }
}
