package com.quangnv.service.gateway.data;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
