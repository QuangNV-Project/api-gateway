package com.quangnv.service.gateway.service;

import com.quangnv.service.gateway.data.UserDto;
import reactor.core.publisher.Mono;

public interface IAuthService {
    Mono<UserDto> validateToken(String token);
}
