package com.quangnv.service.gateway.data;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "cors")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CorsProperties {
    boolean allowCredentials;
    List<String> allowedOrigins;
    List<String> allowedMethods;
    List<String> allowedHeaders;
    List<String> exposedHeaders;

    public void setAllowedOrigins(String origins) {
        this.allowedOrigins = split(origins);
    }

    public void setAllowedMethods(String methods) {
        this.allowedMethods = split(methods);
    }

    public void setAllowedHeaders(String headers) {
        this.allowedHeaders = split(headers);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .toList();
    }
}
