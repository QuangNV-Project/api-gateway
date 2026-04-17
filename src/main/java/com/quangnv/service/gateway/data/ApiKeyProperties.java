package com.quangnv.service.gateway.data;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "gateway.api-key")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiKeyProperties {
    String finTrack;
}

