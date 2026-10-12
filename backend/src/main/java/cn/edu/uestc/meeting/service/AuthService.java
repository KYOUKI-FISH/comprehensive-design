package cn.edu.uestc.meeting.service;

import cn.edu.uestc.meeting.common.BusinessException;
import cn.edu.uestc.meeting.dto.LoginRequest;
import cn.edu.uestc.meeting.dto.LoginResponse;
import cn.edu.uestc.meeting.entity.User;
import cn.edu.uestc.meeting.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录相关的骨架实现 —— 对应 DFD 加工 <b>P1 验证登录</b>、功能需求 <b>FR-01／FR-02</b>。
 *
 * <p>输入数据流：账号、密码；输出数据流：登录结果（成功／失败）、角色。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 校验账号密码并返回角色信息。
     *
     * <p>安全口径（骨架阶段就定，不要图省事改掉）：
     * <ul>
     *   <li>用户不存在与密码错误，返回<b>同一句提示</b>，不泄露"这个账号存在"；</li>
     *   <li>停用账号（status=0）单独提示，便于管理员自查；</li>
     *   <li>日志里<b>只记账号名，不记密码</b>。</li>
     * </ul>
     *
     * @throws BusinessException 401 —— 账号或密码错误 / 账号已停用
     */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败：账号={}", request.getUsername());
            throw new BusinessException(401, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("登录失败：账号已停用，账号={}", request.getUsername());
            throw new BusinessException(401, "账号已被停用，请联系管理员");
        }

        log.info("登录成功：账号={}，角色={}", user.getUsername(), user.getRole());
        return new LoginResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }
}
