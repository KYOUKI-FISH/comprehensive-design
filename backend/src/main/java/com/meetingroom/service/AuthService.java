package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.LoginRequest;
import com.meetingroom.dto.LoginResponse;
import com.meetingroom.entity.User;
import com.meetingroom.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录服务（DFD 加工 P1 验证登录 → 功能需求 FR-01、FR-02）。
 *
 * <p>密码校验：种子数据以 BCrypt 摘要存储（见 {@code database/02_seed.sql}），
 * 比对走 {@link PasswordEncoder#matches(CharSequence, String)}——
 * BCrypt 每次编码都混入随机盐，同一明文每次生成的密文都不同，不能拿字符串相等去比。</p>
 */
@Service
public class AuthService {

    /** 账号状态：正常 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
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

        // ★ BCrypt 摘要比对：种子数据里的密文对应明文 123456（见 README「默认账号」）。
        //   账号不存在时同样落到这一个分支，保证两种失败的响应完全相同（防账号枚举）。
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "账号或密码错误");
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_FORBIDDEN, "该账号已被停用，请联系管理员");
        }

        return new LoginResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}
