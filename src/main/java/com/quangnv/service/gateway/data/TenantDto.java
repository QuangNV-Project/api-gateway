package com.quangnv.service.gateway.data;

import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class TenantDto {
    String domain;
    Long tenantId;
}
