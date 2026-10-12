package cn.edu.uestc.meeting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求入参（P1 验证登录）。
 *
 * <p>只含账号与密码——本系统的账号是<b>系统自建账号</b>（种子数据中的 student01／admin01），
 * 不涉及任何第三方或学校统一身份认证对接（该功能列在本学期暂不实现清单内）。
 */
public class LoginRequest {

    @NotBlank(message = "不能为空")
    private String username;

    @NotBlank(message = "不能为空")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
