package com.quangnv.service.gateway.exception;

public class PatValidationException extends RuntimeException {
    public PatValidationException(String message) {
        super(message);
    }

    public PatValidationException(String message, Throwable cause) {
        super(message, cause);
    }

}
