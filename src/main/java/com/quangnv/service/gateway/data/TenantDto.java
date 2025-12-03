package com.quangnv.service.gateway.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class TenantDto {
    Long tenantId;
    String domainName;
    String siteTitle;
    String projectType;
    Long projectId;
}
