package com.quangnv.service.gateway.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogApiAspect {

    private static final int MAX_ARG_LOG_LENGTH = 2_000;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.quangnv.service.gateway.logging.LogApi)")
    public Object logApi(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
        Object[] args = joinPoint.getArgs();

        String argsStr = formatArgs(args);
        log.info("[API] >>> {} | args: {}", methodName, truncate(argsStr, MAX_ARG_LOG_LENGTH));

        long start = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            log.error("[API] <<< {} | exception: {} - {}", methodName, t.getClass().getSimpleName(), t.getMessage());
            throw t;
        }

        String resultStr = formatResult(result);
        log.info("[API] <<< {} | duration={}ms | response: {}", methodName, System.currentTimeMillis() - start, truncate(resultStr, MAX_ARG_LOG_LENGTH));
        return result;
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        return Arrays.stream(args)
                .map(this::toJsonSafe)
                .collect(Collectors.joining(", "));
    }

    private String formatResult(Object result) {
        return toJsonSafe(result);
    }

    private String toJsonSafe(Object o) {
        if (o == null) return "null";
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return o.toString();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "... [truncated]";
    }
}
