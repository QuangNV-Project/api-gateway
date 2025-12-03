package com.quangnv.service.gateway.data;

import com.quangnv.service.utility_shared.constant.RoleValue;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class UserDto {
    String userId;
    String userName;
    List<RoleValue> roles;
}
