package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.AdminBookingQuery;
import com.meetingroom.dto.PageResult;
import com.meetingroom.dto.ReservationDetailDTO;
import com.meetingroom.service.AdminBookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 管理端预约接口（第 10 周，李知渔）。
 *
 * <pre>
 * GET  /api/admin/bookings                    接口 #9  全部预约（#9 可按会议室／日期筛，FR-15）
 *      查询串：userId? roomId? status? startDate? endDate? pageNum? pageSize?
 * PUT  /api/admin/bookings/{id}/cancel         接口 #10 强制取消异常预约（FR-16）
 *      请求体：{ "reason": "该会议已取消，场地空出" }    ← 必填，不少于 5 个字符
 *
 * 成功：{ "code":200, "message":"ok", "data":{...} }
 * 无权限（普通用户调）：{ "code":403, "message":"无权访问该功能", "data":null }  ← 由 AuthInterceptor 拦
 * 原因缺失：{ "code":400, "message":"请填写取消原因（不少于 5 个字符）", "data":null }
 * 重复取消：{ "code":400, "message":"已取消的预约不能重复取消", "data":null }
 * </pre>
 *
 * <p><b>★ 路径统一挂在 {@code /api/admin/**} 下</b>：{@code AuthInterceptor} 按这个前缀
 * 判定"仅管理员"，接口一旦不在前缀内，普通用户就能直接调到（NF-03 的边界就漏了）。
 * 新增管理端接口时务必保持该前缀。</p>
 */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    public AdminBookingController(AdminBookingService adminBookingService) {
        this.adminBookingService = adminBookingService;
    }

    /**
     * 全部预约（接口 #9）。
     *
     * <p>筛选参数全部可选；一条都不传即返回全部（按开始时间倒序）。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<ReservationDetailDTO>> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {

        AdminBookingQuery query = new AdminBookingQuery();
        query.setUserId(userId);
        query.setRoomId(roomId);
        query.setStatus(status);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return ApiResponse.success(adminBookingService.list(query));
    }

    /**
     * 强制取消异常预约（接口 #10，FR-16）。
     *
     * <p>★ 请求体用 {@code Map<String,String>} 而不是专门的 DTO：这里只有一个字段，
     * 建 DTO 是为了类型安全，而一个单字段 DTO 反而让"字段名写错"这件事从
     * 编译期错误退化成运行时拿到 null（Jackson 找不到字段就留 null）——
     * 两种写法都没有编译期保护，那就选少一个文件的。校验在 Service 里做得更细。</p>
     */
    @PutMapping("/{id}/cancel")
    public ApiResponse<ReservationDetailDTO> forceCancel(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.success(adminBookingService.forceCancel(id, reason));
    }
}
