package cn.edu.uestc.meeting.common;

/**
 * 统一返回体（接口出参的公共外壳）。
 *
 * <p>前后端分离（A2）下，前后端对"一次调用成功/失败"必须有共同语言，
 * 否则联调时每个接口各写一套判断。本类是第 6 周骨架先定的最小约定，
 * 第 7 周接口设计阶段在此基础上固化字段名与错误码表（见 docs/week06/周推进计划.md §6）。
 *
 * <p>错误码约定（第 7 周细化）：
 * <ul>
 *   <li>200 —— 成功</li>
 *   <li>400 —— 入参不合法（校验失败）</li>
 *   <li>401 —— 账号或密码错误 / 账号已停用</li>
 *   <li>500 —— 服务器内部错误</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
public class ApiResult<T> {

    /** 业务状态码，见类注释 */
    private int code;

    /** 提示信息，前端可直接展示 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    public ApiResult() {
    }

    public ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "成功", data);
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        return new ApiResult<>(200, message, data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
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
