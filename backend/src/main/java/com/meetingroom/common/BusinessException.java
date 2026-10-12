package com.meetingroom.common;

/**
 * 业务异常：由 Service 层抛出，统一由 {@link GlobalExceptionHandler} 转成
 * {@link ApiResponse}（code 非 200）。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
