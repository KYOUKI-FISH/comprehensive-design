package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.RoomVO;
import com.meetingroom.service.AdminRoomService;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 管理端会议室管理（第 10 周，李知渔；接口 #11）。
 *
 * <pre>
 * GET    /api/admin/rooms            列表（与前台 #3 同口径，但不过滤停用）
 * POST   /api/admin/rooms            新增（FR-03）
 *        body: { "name":"...", "location":"...", "capacity":20,
 *                "equipment":"投影仪", "remark":"...", "status":"ENABLED" }
 * PUT    /api/admin/rooms/{id}       修改（FR-03／FR-04；status 也走这里，传 DISABLED 即停用）
 * DELETE /api/admin/rooms/{id}       删除（有预约时 409「该会议室存在有效预约，请先处理预约」）
 * </pre>
 *
 * <p><b>★ 为什么 PUT 同时管"停用"：</b>需求清单里启用／停用不是一个独立接口，
 * 而是会议室的一个字段（第 8 周定稿口径 §#11 注释）。单独再开一个
 * {@code PUT /api/admin/rooms/{id}/status} 会让接口清单从 14 条变多，
 * 与第 8 周定稿对不上，第 11 周核对"接口总数 14 条"时会卡住。</p>
 */
@RestController
@RequestMapping("/api/admin/rooms")
public class AdminRoomController {

    private final AdminRoomService adminRoomService;

    public AdminRoomController(AdminRoomService adminRoomService) {
        this.adminRoomService = adminRoomService;
    }

    @GetMapping
    public ApiResponse<List<RoomVO>> list(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(adminRoomService.list(location, minCapacity, status));
    }

    @PostMapping
    public ApiResponse<RoomVO> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(adminRoomService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(adminRoomService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminRoomService.delete(id);
        return ApiResponse.success();
    }
}
