package com.meetingroom.common;

/**
 * 统一返回体（前后端约定的固定结构，第 7 周接口设计阶段固化到 OpenAPI 文档）。
 *
 * <pre>
 * {
 *   "code": 200,
 *   "message": "ok",
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>约定：业务失败也返回 HTTP 200，靠 {@code code} 区分结果，
 * 避免前端在 HTTP 状态码与业务码之间来回判断（技术选型报告"预测问题"提到的接口不一致风险）。</p>
 */
public class ApiResponse<T> {

    /** 成功 */
    public static final int CODE_OK = 200;
    /** 请求参数不合法 */
    public static final int CODE_PARAM_ERROR = 400;
    /** 未认证：账号或密码错误 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 无权限：账号被停用等 */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 服务器内部错误 */
    public static final int CODE_SERVER_ERROR = 500;

    private int code;
    private String message;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(CODE_OK, "ok", data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(CODE_OK, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
