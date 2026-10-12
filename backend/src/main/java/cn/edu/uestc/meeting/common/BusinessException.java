package cn.edu.uestc.meeting.common;

/**
 * 业务异常：用于把"可预期的业务错误"（如账号密码错误）与"程序 Bug"区分开，
 * 由 {@link GlobalExceptionHandler} 统一翻译成 {@link ApiResult}。
 *
 * <p>骨架阶段只有登录用到；第 8～10 周的业务校验（时间规则、冲突检测等）继续复用。
 */
public class BusinessException extends RuntimeException {

    /** 业务状态码，见 {@link ApiResult} */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
