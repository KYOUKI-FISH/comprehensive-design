package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.UserVO;
import com.meetingroom.entity.User;
import com.meetingroom.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端用户管理服务（接口 #12，FR-14 用户账号管理）。
 *
 * <p>三个方法：{@code list}｜{@code create}｜{@code update}。</p>
 *
 * <p><b>★ 口令口径（NF-05）：</b>入参明文只在内存里存在到 {@code passwordEncoder.encode()}
 * 为止；库里只存 BCrypt 摘要；出参 {@link UserVO} 从类型上就没有 password 字段。
 * 三层都堵上，才不会因为"某处忘了置空"把摘要漏进响应。</p>
 *
 * <p><b>★ 每个写方法都标 {@code @Transactional}：</b>账号变更与操作日志必须同事务（NF-14）。</p>
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    /** 接口清单 #12：密码长度不足 6 位即拒绝 */
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_REAL_NAME_LENGTH = 50;
    private static final int MAX_PHONE_LENGTH = 20;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final TokenStore tokenStore;

    public AdminUserService(UserMapper userMapper,
                            PasswordEncoder passwordEncoder,
                            OperationLogService operationLogService,
                            TokenStore tokenStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.tokenStore = tokenStore;
    }

    /**
     * 用户列表（接口 #12 GET，FR-14）。
     *
     * @param status 可空，ACTIVE／DISABLED
     * @param role   可空，USER／ADMIN
     */
    public List<UserVO> list(String status, String role) {
        String normalizedStatus = normalizeEnum(status, "账号状态只能是 ACTIVE（正常）或 DISABLED（停用）",
                STATUS_ACTIVE, STATUS_DISABLED);
        String normalizedRole = normalizeEnum(role, "角色只能是 USER（普通用户）或 ADMIN（管理员）",
                ROLE_USER, ROLE_ADMIN);
        return userMapper.selectList(normalizedStatus, normalizedRole)
                .stream().map(UserVO::from).collect(Collectors.toList());
    }

    /**
     * 新建账号（接口 #12 POST，FR-14）。
     *
     * <p>校验顺序：账号必填 → 查重 → 姓名必填 → 密码长度 → 角色枚举。
     * 顺序不是随意的：先报"账号已存在"比先报"密码太短"更符合使用者的操作习惯
     * （他刚填完账号就被告知重名，不必把密码也填一遍才发现）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(Map<String, Object> body) {
        Long operatorId = requireOperator();
        String operatorUsername = CurrentUser.username();

        String username = requireText(body, "username", "请填写登录账号", MAX_USERNAME_LENGTH);
        if (userMapper.selectByUsernameForCheck(username) != null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "账号已存在");
        }

        String realName = requireText(body, "realName", "请填写姓名", MAX_REAL_NAME_LENGTH);
        String password = validatePassword(body.get("password"));
        String role = parseRole(body.get("role"), ROLE_USER);
        String phone = optionalText(body.get("phone"), MAX_PHONE_LENGTH);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName);
        user.setRole(role);
        user.setPhone(phone);
        user.setStatus(STATUS_ACTIVE);

        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "账号已存在");
        }

        operationLogService.log(operatorId, operatorUsername,
                OperationLogService.USER_CREATE, "USER", user.getId(),
                null,
                "{\"username\":\"" + escape(username) + "\",\"role\":\"" + escape(role)
                        + "\",\"status\":\"" + STATUS_ACTIVE + "\"}",
                "管理员新建账号：" + username + "（" + realName + "）");

        log.info("新建账号：id={}, username={}, role={}, operator={}",
                user.getId(), username, role, operatorUsername);
        return UserVO.from(userMapper.selectById(user.getId()));
    }

    /**
     * 修改账号（接口 #12 PUT，FR-14／NF-05）。
     *
     * <p>支持改：{@code status}（启停）、{@code realName}、{@code phone}、{@code role}、{@code password}。</p>
     *
     * <p><b>★ 三个必须拦住的危险操作：</b></p>
     * <ol>
     *   <li><b>不能停用自己当前登录的账号</b>——照接口清单原文；否则点完就把自己锁在门外，
     *       且没有第二个管理员时系统再也进不去。</li>
     *   <li><b>不能把最后一个管理员降级为普通用户</b>——接口清单没写这一条，但停用自己能拦住的前提是
     *       "还有别的管理员能登进来"；降级完一个管理员都不剩，管理端就永久失守了。</li>
     *   <li><b>停用后立即作废该用户全部会话</b>（NF-05）——只改库里的 status 是不够的：
     *       {@code AuthInterceptor} 虽然每次请求都回查状态，会拒掉后续请求，
     *       但主动清掉内存里的会话能让"已登录的页面立刻失效"，语义更干净。</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(Long id, Map<String, Object> body) {
        Long operatorId = requireOperator();
        String operatorUsername = CurrentUser.username();

        User before = userMapper.selectById(id);
        if (before == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "用户不存在");
        }

        User patch = new User();
        patch.setId(id);
        boolean changed = false;
        boolean disabling = false;
        boolean roleDemotion = false;

        if (body != null) {
            if (body.get("status") != null) {
                String status = normalizeEnum(body.get("status").toString(),
                        "账号状态只能是 ACTIVE（正常）或 DISABLED（停用）",
                        STATUS_ACTIVE, STATUS_DISABLED);
                if (STATUS_DISABLED.equals(status)) {
                    // ★ 危险操作 1：不能停用自己
                    if (id.equals(operatorId)) {
                        throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                                "不能停用当前登录的管理员账号");
                    }
                    disabling = !STATUS_DISABLED.equals(before.getStatus());
                }
                patch.setStatus(status);
                changed = true;
            }
            if (body.get("realName") != null) {
                patch.setRealName(requireText(body, "realName", "请填写姓名", MAX_REAL_NAME_LENGTH));
                changed = true;
            }
            if (body.get("phone") != null) {
                patch.setPhone(optionalText(body.get("phone"), MAX_PHONE_LENGTH));
                changed = true;
            }
            if (body.get("role") != null) {
                String role = parseRole(body.get("role"), null);
                // ★ 危险操作 2：不能把最后一个管理员降级
                if (ROLE_ADMIN.equals(before.getRole()) && ROLE_USER.equals(role)) {
                    roleDemotion = true;
                }
                patch.setRole(role);
                changed = true;
            }
            if (body.get("password") != null) {
                patch.setPassword(passwordEncoder.encode(validatePassword(body.get("password"))));
                changed = true;
            }
        }

        if (!changed) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "请至少提供一个要修改的字段（status／realName／phone／role／password）");
        }

        // 降级前先确认还有别的管理员在，否则管理端会被锁死
        if (roleDemotion && userMapper.countActiveAdmins() <= 1) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "系统至少需要保留一个可用状态的管理员账号");
        }

        try {
            userMapper.updateSelective(patch);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "账号信息不符合要求");
        }

        // ★ 危险操作 3：停用后立刻作废该用户全部会话（NF-05）
        if (disabling) {
            tokenStore.revokeAllOf(id);
        }

        // ★ 日志类型的选择只有三种合法落点，其余情况**不写日志**：
        //   ① 改了 status   → USER_DISABLE / USER_ENABLE
        //   ② 只改了 role   → ROLE_CHANGE
        //   ③ 只改了姓名／电话／密码 → schema 的 9 个类型里没有"用户信息修改"，
        //      而第 11 周约定不改表结构，加不了新类型。
        //      这时**不写** operation_log：硬塞一个 ROLE_CHANGE 会让审计
        //      "按类型筛角色变更"时冒出根本没改角色的记录，比不记更糟。
        //      NF-14 列明的敏感操作是"新增／停用用户、新增／删除会议室、强制取消预约、角色变更"，
        //      改姓名电话不在此列，不记不违规。
        String logType = null;
        if (patch.getStatus() != null) {
            logType = STATUS_DISABLED.equals(patch.getStatus())
                    ? OperationLogService.USER_DISABLE
                    : OperationLogService.USER_ENABLE;
        } else if (patch.getRole() != null) {
            logType = OperationLogService.ROLE_CHANGE;
        }

        if (logType != null) {
            operationLogService.log(operatorId, operatorUsername,
                    logType, "USER", id,
                    "{\"role\":\"" + escape(before.getRole()) + "\",\"status\":\"" + escape(before.getStatus()) + "\"}",
                    "{\"role\":\"" + escape(patch.getRole() == null ? before.getRole() : patch.getRole())
                            + "\",\"status\":\"" + escape(patch.getStatus() == null ? before.getStatus() : patch.getStatus()) + "\"}",
                    buildUpdateRemark(before, patch));
        }

        log.info("修改账号：id={}, username={}, operator={}, disabling={}, roleDemotion={}",
                id, before.getUsername(), operatorUsername, disabling, roleDemotion);
        return UserVO.from(userMapper.selectById(id));
    }

    /** 更新备注：写清"谁改了什么"，审计时不必再去解析 JSON */
    private String buildUpdateRemark(User before, User patch) {
        StringBuilder sb = new StringBuilder("管理员修改账号：").append(before.getUsername());
        if (patch.getStatus() != null && !patch.getStatus().equals(before.getStatus())) {
            sb.append("；状态 ").append(before.getStatus()).append(" → ").append(patch.getStatus());
        }
        if (patch.getRole() != null && !patch.getRole().equals(before.getRole())) {
            sb.append("；角色 ").append(before.getRole()).append(" → ").append(patch.getRole());
        }
        if (patch.getRealName() != null) {
            sb.append("；姓名改为 ").append(patch.getRealName());
        }
        if (patch.getPhone() != null) {
            sb.append("；联系电话改为 ").append(patch.getPhone());
        }
        if (patch.getPassword() != null) {
            // ★ 备注里绝不写明文或摘要，只说"已重置"
            sb.append("；登录口令已重置");
        }
        return sb.toString();
    }

    private Long requireOperator() {
        Long operatorId = CurrentUser.userId();
        if (operatorId == null || CurrentUser.username() == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录");
        }
        return operatorId;
    }

    /** 口令长度校验；返回原文供调用方 encode */
    private String validatePassword(Object raw) {
        String password = raw == null ? "" : raw.toString();
        if (password.isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写登录口令");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "密码长度不足 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "密码不能超过 " + MAX_PASSWORD_LENGTH + " 位");
        }
        return password;
    }

    private String requireText(Map<String, Object> body, String key, String emptyMessage, int maxLength) {
        Object raw = body == null ? null : body.get(key);
        String value = raw == null ? "" : raw.toString().trim();
        if (value.isEmpty()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, emptyMessage);
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "内容不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private String optionalText(Object raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "内容不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private String parseRole(Object raw, String defaultValue) {
        if (raw == null || raw.toString().isBlank()) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "角色只能是 USER（普通用户）或 ADMIN（管理员）");
        }
        String role = raw.toString().trim().toUpperCase();
        if (!ROLE_USER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "角色只能是 USER（普通用户）或 ADMIN（管理员）");
        }
        return role;
    }

    /** 枚举规范化：null／空白放行（表示不筛），非法值报错 */
    private String normalizeEnum(String raw, String errorMessage, String... allowed) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, errorMessage);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
