package com.meetingroom.common;

/**
 * 业务异常：由 Service 层抛出，统一由 {@link GlobalExceptionHandler} 转成
 * {@link ApiResponse}（code 非 200）。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 需要随失败响应一起返回的业务数据（如冲突时的 occupiedSlots），可为 null。
     * ★ FR-10 要求冲突时把被占用时段一并返回，只有 code + message 装不下这个载荷。
     */
    private final transient Object data;

    public BusinessException(int code, String message) {
        this(code, message, null);
    }

    public BusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
