package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.UserVO;
import com.meetingroom.entity.User;
import com.meetingroom.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端用户服务用例（接口 #12，FR-14／NF-05）。
 *
 * <p>重点覆盖三个"点一下就把自己锁在门外"的危险操作：</p>
 * <ol>
 *   <li>停用自己当前登录的账号；</li>
 *   <li>把最后一个可用管理员降级成普通用户；</li>
 *   <li>口令明文入库（必须 BCrypt）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ADMIN_NAME = "admin";
    private static final Long TARGET_ID = 2L;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private TokenStore tokenStore;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userMapper, passwordEncoder,
                operationLogService, tokenStore);
        CurrentUser.set(new TokenStore.Session(ADMIN_ID, ADMIN_NAME, "ADMIN",
                LocalDateTime.now().plusMinutes(30)));
    }

    @AfterEach
    void logout() {
        CurrentUser.clear();
    }

    private User user(Long id, String username, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("$2a$10$abcdefghijklmnopqrstuv");  // 假摘要，够长即可
        user.setRealName("张三");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    // ---------------------------------------------------------------------
    // 危险操作 1：不能停用自己
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("停用自己当前登录的账号 → 400「不能停用当前登录的管理员账号」，且不写库")
    void 不能停用自己() {
        when(userMapper.selectById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "admin", "ADMIN", "ACTIVE"));

        Map<String, Object> body = new HashMap<>();
        body.put("status", "DISABLED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.update(ADMIN_ID, body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("不能停用当前登录"));
        verify(userMapper, never()).updateSelective(any());
        verify(tokenStore, never()).revokeAllOf(anyLong());
    }

    // ---------------------------------------------------------------------
    // 危险操作 2：不能降级最后一个管理员
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("把最后一个可用管理员降级 → 400「至少需要保留一个可用状态的管理员账号」")
    void 不能降级最后一个管理员() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "admin2", "ADMIN", "ACTIVE"));
        when(userMapper.countActiveAdmins()).thenReturn(1L);

        Map<String, Object> body = new HashMap<>();
        body.put("role", "USER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.update(TARGET_ID, body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("管理员"));
        verify(userMapper, never()).updateSelective(any());
    }

    @Test
    @DisplayName("还有别的管理员时，降级可放行")
    void 有备份管理员可降级() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "admin2", "ADMIN", "ACTIVE"));
        when(userMapper.countActiveAdmins()).thenReturn(2L);

        Map<String, Object> body = new HashMap<>();
        body.put("role", "USER");
        adminUserService.update(TARGET_ID, body);

        ArgumentCaptor<User> patchCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateSelective(patchCaptor.capture());
        assertEquals("USER", patchCaptor.getValue().getRole());
    }

    // ---------------------------------------------------------------------
    // 停用他人：要作废会话
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("停用他人 → 写库 + 立即作废该用户全部会话（NF-05）")
    void 停用他人并作废会话() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "user1", "USER", "ACTIVE"));

        Map<String, Object> body = new HashMap<>();
        body.put("status", "DISABLED");
        adminUserService.update(TARGET_ID, body);

        ArgumentCaptor<User> patchCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateSelective(patchCaptor.capture());
        assertEquals("DISABLED", patchCaptor.getValue().getStatus());
        // ★ NF-05：只改 status 不够，已登录的会话要立刻失效
        verify(tokenStore).revokeAllOf(TARGET_ID);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), typeCaptor.capture(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
        assertEquals("USER_DISABLE", typeCaptor.getValue());
    }

    @Test
    @DisplayName("启用他人 → 日志类型 USER_ENABLE，不作废会话")
    void 启用他人() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "user1", "USER", "DISABLED"));

        Map<String, Object> body = new HashMap<>();
        body.put("status", "ACTIVE");
        adminUserService.update(TARGET_ID, body);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), typeCaptor.capture(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
        assertEquals("USER_ENABLE", typeCaptor.getValue());
        verify(tokenStore, never()).revokeAllOf(anyLong());
    }

    @Test
    @DisplayName("只改姓名／电话 → 不写日志（9 类里没有'用户信息修改'，硬塞会污染审计筛选）")
    void 只改姓名不写日志() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "user1", "USER", "ACTIVE"));

        Map<String, Object> body = new HashMap<>();
        body.put("realName", "李四");
        adminUserService.update(TARGET_ID, body);

        verify(userMapper).updateSelective(any());
        verify(operationLogService, never()).log(anyLong(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------
    // 新建账号
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("新建账号：口令存 BCrypt 摘要（不是明文），默认角色 USER、状态 ACTIVE")
    void 新建账号密码加密() {
        when(userMapper.selectByUsernameForCheck("user3")).thenReturn(null);
        when(userMapper.selectById(any())).thenReturn(user(9L, "user3", "USER", "ACTIVE"));

        Map<String, Object> body = new HashMap<>();
        body.put("username", "user3");
        body.put("password", "abc123456");
        body.put("realName", "王五");
        adminUserService.create(body);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();

        // ★ NF-05 核心断言：库里绝不能是明文
        assertNotEquals("abc123456", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"), "应为 BCrypt 摘要，实际：" + saved.getPassword());
        // 摘要可被 matches 正确校验（说明存的是同一口令）
        assertTrue(passwordEncoder.matches("abc123456", saved.getPassword()));

        assertEquals("USER", saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
    }

    @Test
    @DisplayName("新建账号时密码只有 5 位 → 400「密码长度不足 6 位」")
    void 密码过短被拒() {
        when(userMapper.selectByUsernameForCheck("user3")).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "user3");
        body.put("password", "12345");
        body.put("realName", "王五");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.create(body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("6"));
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新建账号时账号已存在 → 400「账号已存在」，且不写库")
    void 账号重复被拒() {
        when(userMapper.selectByUsernameForCheck("admin"))
                .thenReturn(user(ADMIN_ID, "admin", "ADMIN", "ACTIVE"));

        Map<String, Object> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "123456");
        body.put("realName", "重复");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.create(body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertEquals("账号已存在", ex.getMessage());
        verify(userMapper, never()).insert(any());
    }

    // ---------------------------------------------------------------------
    // 出参不含口令
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("UserVO 在类型层面就没有 password 字段（出参结构里不可能出现摘要）")
    void 出参无密码字段() {
        boolean hasPasswordField = false;
        for (var field : UserVO.class.getDeclaredFields()) {
            if ("password".equals(field.getName())) {
                hasPasswordField = true;
                break;
            }
        }
        assertTrue(!hasPasswordField, "UserVO 不应包含 password 字段");
    }

    @Test
    @DisplayName("列表出参转换后不含口令摘要")
    void 列表不含密码() {
        when(userMapper.selectList(null, null))
                .thenReturn(java.util.List.of(user(1L, "admin", "ADMIN", "ACTIVE")));

        var list = adminUserService.list(null, null);

        assertEquals(1, list.size());
        UserVO vo = list.get(0);
        assertNotNull(vo.getUsername());
        assertEquals("admin", vo.getUsername());
    }

    @Test
    @DisplayName("列表筛选状态非法 → 400")
    void 列表状态非法() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.list("DELETED", null));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("空 body 更新 → 400，不写库")
    void 空更新被拒() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "user1", "USER", "ACTIVE"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.update(TARGET_ID, new HashMap<>()));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        verify(userMapper, never()).updateSelective(any());
    }
}
