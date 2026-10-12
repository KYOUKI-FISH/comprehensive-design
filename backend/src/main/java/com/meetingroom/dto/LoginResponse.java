package com.meetingroom.dto;

/**
 * 登录响应体。
 *
 * <p>★ 本周只到"账号密码校验 + 返回角色"为止：
 * 会话 / JWT 令牌属于登录功能（FR-01／FR-02）的完整实现，按计划在第 9～10 周落地，
 * 因此这里的 {@code token} 字段暂不提供——前端本周把 userId 与 role 存本地即可完成页面跳转验证。</p>
 */
public class LoginResponse {

    private Long userId;
    private String username;
    private String realName;
    /** 角色：USER / ADMIN，前端据此决定进用户端还是管理端（FR-02） */
    private String role;

    public LoginResponse() {
    }

    public LoginResponse(Long userId, String username, String realName, String role) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
