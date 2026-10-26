package com.meetingroom.common;

/**
 * 业务异常：由 Service 层抛出，统一由 {@link GlobalExceptionHandler} 转成
 * {@link ApiResponse}（code 非 200）。
 *
 * <p>第 9 周起新增 {@code data} 字段：409 冲突响应需要把<strong>被占用的时段</strong>
 * 一并返回给前端（FR-10「提示预约冲突」要求系统说明"已被占用的时间段"），
 * 而原来的 {@code ApiResponse.error(code, message)} 只能带 code 与 message。</p>
 */
public class BusinessException extends RuntimeException {

    private final int code;
    /** 附带的业务数据；可为 null（如 400／401 这类纯提示型失败） */
    private final Object data;

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
