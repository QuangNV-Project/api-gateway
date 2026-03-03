package com.quangnv.service.gateway.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu API cần log đầu vào và response (dùng với AOP).
 * Áp dụng cho method trong @RestController để dễ tracking khi lỗi.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogApi {
}
