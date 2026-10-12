package cn.edu.uestc.meeting.controller;

import cn.edu.uestc.meeting.common.ApiResult;
import cn.edu.uestc.meeting.dto.LoginRequest;
import cn.edu.uestc.meeting.dto.LoginResponse;
import cn.edu.uestc.meeting.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口（骨架级）—— P1 验证登录 / FR-01 用户登录、FR-02 角色区分。
 *
 * <pre>
 * POST /api/auth/login
 * 入参：{ "username": "student01", "password": "123456" }
 * 出参：{ "code": 200, "message": "成功", "data": { "userId":1, "username":"student01",
 *         "realName":"张同学", "role":"USER" } }
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
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok("登录成功", authService.login(request));
    }
}
