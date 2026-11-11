package com.meetingroom.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：保证任何异常都以统一返回体形式返回，前端不必解析 HTML 错误页。
 *
 * <p>★ BUG-07（第 12 周）：HTTP 状态行与 body.code 双写且数值一致——
 * 业务失败不再一律 HTTP 200，传输层状态码 = 业务码（400/401/403/404/409/500），
 * body 结构 {code,message,data} 与 code 语义完全不变（FR-10 的 occupiedSlots 照常回带）。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 统一出口：HTTP 状态码与 body.code 同值。BusinessException 的 code 是动态的，不能用 @ResponseStatus 写死 */
    private <T> ResponseEntity<ApiResponse<T>> respond(ApiResponse<T> body) {
        return ResponseEntity.status(body.getCode()).body(body);
    }

    /** 业务异常：可直接展示给用户的提示；带 data 时一并回带（如 409 的被占用时段） */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return respond(ApiResponse.error(e.getCode(), e.getMessage(), e.getData()));
    }

    /** 参数类异常：缺参、类型不匹配、请求体不是合法 JSON */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("请求参数不合法：{}", e.getMessage());
        return respond(ApiResponse.error(ApiResponse.CODE_PARAM_ERROR, "请求参数不合法：" + e.getMessage()));
    }

    /**
     * 接口 / 资源不存在。★ 必须有这个处理器：Spring Boot 3.2 对未匹配的路径抛
     * {@code NoResourceFoundException}，若交给下面的兜底处理器，会把 404 报成 500，
     * 前端无法区分"接口写错了"和"后端崩了"。（本机实测发现，2026-10-12）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        log.warn("请求的接口不存在：{}", e.getResourcePath());
        return respond(ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "请求的接口不存在：" + e.getResourcePath()));
    }

    /** 请求方法不支持（如把 GET 接口用 POST 调）；业务码口径保持 400 不变 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMessage());
        return respond(ApiResponse.error(ApiResponse.CODE_PARAM_ERROR, "请求方法不支持：" + e.getMessage()));
    }

    /** 兜底：未预期异常记日志，对外给中性提示，不泄露堆栈 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("未预期异常", e);
        return respond(ApiResponse.error(ApiResponse.CODE_SERVER_ERROR, "服务器内部错误，请查看后端控制台日志"));
    }
}
