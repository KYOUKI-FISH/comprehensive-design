package com.meetingroom.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：保证任何异常都以统一返回体形式返回，前端不必解析 HTML 错误页。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可直接展示给用户的提示 */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 参数类异常：缺参、类型不匹配、请求体不是合法 JSON */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleBadRequest(Exception e) {
        log.warn("请求参数不合法：{}", e.getMessage());
        return ApiResponse.error(ApiResponse.CODE_PARAM_ERROR, "请求参数不合法：" + e.getMessage());
    }

    /** 兜底：未预期异常记日志，对外给中性提示，不泄露堆栈 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.error(ApiResponse.CODE_SERVER_ERROR, "服务器内部错误，请查看后端控制台日志");
    }
}
