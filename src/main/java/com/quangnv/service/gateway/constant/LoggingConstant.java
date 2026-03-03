package com.quangnv.service.gateway.constant;

/**
 * Hằng số dùng cho logging request/response (tracking, debug).
 */
public final class LoggingConstant {

    private LoggingConstant() {
    }

    /** Header chứa ID request để tracking xuyên suốt (correlation). */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Attribute key trong ServerWebExchange để lưu requestId. */
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    /** Giới hạn độ dài body (bytes) khi log để tránh quá tải. */
    public static final int MAX_BODY_LOG_BYTES = 4_096;

    /** Các header nhạy cảm chỉ in dạng mask (ví dụ: Authorization). */
    public static final String MASKED_HEADER_VALUE = "***";
}
