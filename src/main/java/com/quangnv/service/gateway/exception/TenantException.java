package com.quangnv.service.gateway.exception;

public class TenantException extends RuntimeException {
    public enum Kind {
        NOT_FOUND,
        CLIENT_ERROR,
        UPSTREAM_ERROR,
        MALFORMED_RESPONSE,
        TRANSPORT_ERROR
    }

    private final Kind kind;
    private final int status;

    public TenantException(Kind kind, int status, String message) {
        super(message);
        this.kind = kind;
        this.status = status;
    }

    public TenantException(Kind kind, int status, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.status = status;
    }

    public Kind getKind() {
        return kind;
    }

    public int getStatus() {
        return status;
    }

    public String getSafeMessage() {
        return switch (kind) {
            case NOT_FOUND -> "Tenant was not found";
            case CLIENT_ERROR -> "Tenant request was rejected";
            case UPSTREAM_ERROR, TRANSPORT_ERROR -> "Tenant service is unavailable";
            case MALFORMED_RESPONSE -> "Tenant service returned an invalid response";
        };
    }
}
