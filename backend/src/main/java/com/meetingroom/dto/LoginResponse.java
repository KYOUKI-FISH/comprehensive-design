package com.meetingroom.dto;

import com.meetingroom.entity.User;

/**
 * 登录响应体。
 *
 * <p>第 9 周起本类<strong>带令牌</strong>（第 6 周骨架阶段不带，因为后端当时不发令牌）：
 * 后端签发会话令牌并返回，前端存下来并在后续请求里通过
 * {@code Authorization: Bearer <token>} 带回（FR-01、NF-04）。
 *
 * <p>★ 令牌不写进 Cookie，用 Header 传——避免 CSRF，也让"登出即失效"更直接。
 */
public class LoginResponse {

    private Long userId;
    private String username;
    private String realName;
    /** 角色：USER / ADMIN，前端据此决定进用户端还是管理端（FR-02） */
    private String role;
    /**
     * 会话令牌（第 9 周新增；NF-04 有有效期 30 分钟，过期后需重新登录）。
     *
     * <p>⚠️ 令牌只在登录响应里出现一次，后续请求一律走 Authorization 头。
     * 令牌本身不含任何用户信息，是一串随机串。</p>
     */
    private String token;

    public LoginResponse() {
    }

    public LoginResponse(Long userId, String username, String realName, String role) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    /**
     * 从用户实体构造登录响应（不含令牌，令牌由 Service 签发后填入）。
     */
    public static LoginResponse from(User user) {
        return new LoginResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
