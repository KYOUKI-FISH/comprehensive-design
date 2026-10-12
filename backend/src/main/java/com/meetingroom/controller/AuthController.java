package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.LoginRequest;
import com.meetingroom.dto.LoginResponse;
import com.meetingroom.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口（环境验证通路之一）。
 *
 * <pre>
 * POST /api/auth/login
 * 请求体：{ "username": "admin", "password": "123456" }
 * 成功  ：{ "code": 200, "message": "ok", "data": { "userId": 1, "username": "admin", "realName": "系统管理员", "role": "ADMIN" } }
 * 失败  ：{ "code": 401, "message": "账号或密码错误", "data": null }
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
