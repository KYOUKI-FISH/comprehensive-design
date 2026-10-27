package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.LoginRequest;
import com.meetingroom.dto.LoginResponse;
import com.meetingroom.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录／登出接口（第 9 周；对应 FR-01 用户登录、NF-04 会话安全）。
 *
 * <pre>
 * POST /api/auth/login
 * 请求体：{ "username": "admin", "password": "123456" }
 * 成功  ：{ "code": 200, "message": "ok", "data": { "userId": 1, "username": "admin",
 *         "realName": "系统管理员", "role": "ADMIN", "token": "<会话令牌>" } }
 * 失败  ：{ "code": 401, "message": "账号或密码错误", "data": null }
 *
 * POST /api/auth/logout
 * 入参  ：Authorization: Bearer &lt;token&gt;
 * 成功  ：{ "code": 200, "message": "ok", "data": null }
 * 说明  ：登出后该令牌立即作废，用旧令牌再发任何请求都会被拒（NF-04）
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 登出（第 8 周接口清单第 2 条，NF-04）。
     *
     * <p>★ 走的是服务端作废，不是前端删 token：只在前端清掉 token 的话，
     * 旧令牌在有效期内仍能被用来调接口（会话劫持场景）。</p>
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            token = header.substring(BEARER_PREFIX.length()).trim();
        }
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        authService.logout(token);
        return ApiResponse.success();
    }
}
