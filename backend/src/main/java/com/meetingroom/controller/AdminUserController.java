package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.UserVO;
import com.meetingroom.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端用户管理（第 10 周，李知渔；接口 #12，FR-14）。
 *
 * <pre>
 * GET  /api/admin/users          列表（status?、role? 可筛；★ 出参不含 password）
 * POST /api/admin/users          建号（FR-14）
 *      body: { "username":"user3", "password":"123456", "realName":"王五",
 *              "phone":"13800000004", "role":"USER" }
 * PUT  /api/admin/users/{id}     改账号：启停／改名／改电话／改角色／重置口令
 *      body: { "status":"DISABLED" } 或 { "realName":"..." } 等，至少一项
 * </pre>
 *
 * <p><b>★ 没有 DELETE：</b>需求清单 FR-14 只要求"新建与停用"，删除用户不在范围内。
 * 另外 {@code reservation.user_id} 与 {@code operation_log.operator_id} 都是
 * {@code ON DELETE RESTRICT}，删用户会破坏历史预约和审计日志的归属——
 * 这也是为什么"停用"才是正确的下线方式。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<List<UserVO>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String role) {
        return ApiResponse.success(adminUserService.list(status, role));
    }

    @PostMapping
    public ApiResponse<UserVO> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(adminUserService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(adminUserService.update(id, body));
    }
}
