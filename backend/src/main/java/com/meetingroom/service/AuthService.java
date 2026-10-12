package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.LoginRequest;
import com.meetingroom.dto.LoginResponse;
import com.meetingroom.entity.User;
import com.meetingroom.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 登录服务（DFD 加工 P1 验证登录 → 功能需求 FR-01、FR-02）。
 */
@Service
public class AuthService {

    /** 账号状态：正常 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final UserMapper userMapper;

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 校验账号密码并返回登录者身份与角色。
     *
     * <p>失败提示一律为"账号或密码错误"，不区分"账号不存在"与"密码错误"，
     * 避免被用来枚举账号（NF 安全类需求）。</p>
     */
    public LoginResponse login(LoginRequest request) {
        if (request == null
                || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "账号和密码不能为空");
        }

        String username = request.getUsername().trim();
        User user = userMapper.selectByUsername(username);

        // ★ 骨架阶段为明文比对；第 9～10 周改用 BCrypt 摘要比对，届时同步迁移种子数据
        if (user == null || !request.getPassword().equals(user.getPassword())) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "账号或密码错误");
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_FORBIDDEN, "该账号已被停用，请联系管理员");
        }

        return new LoginResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}
