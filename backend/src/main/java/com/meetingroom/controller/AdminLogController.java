package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.AdminLogQuery;
import com.meetingroom.dto.OperationLogVO;
import com.meetingroom.dto.PageResult;
import com.meetingroom.service.AdminLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理端操作日志查询（第 10 周，李知渔；接口 #13，NF-14）。
 *
 * <pre>
 * GET /api/admin/logs
 *     查询串：operatorId? operationType? targetType? startDate? endDate? pageNum? pageSize?
 * 出参：{ "code":200, "message":"ok",
 *        "data":{ "total":12, "pageNum":1, "pageSize":20,
 *                 "list":[ { "id":1, "operatorUsername":"admin", "operatorName":"系统管理员",
 *                            "operatorRole":"ADMIN", "operationType":"BOOKING_FORCE_CANCEL",
 *                            "targetType":"BOOKING", "targetId":5,
 *                            "detail":"演示数据：管理员强制取消异常预约",
 *                            "ip":null, "createdAt":"2026-11-02 09:30:00" } ] } }
 * 普通用户访问：{ "code":403, "message":"无权访问该功能", "data":null }
 * </pre>
 *
 * <p><b>★ 没有 DELETE／PUT：</b>NF-14 要求日志"不能被业务界面删除"，
 * 因此本控制器只有 GET。这不是"暂未实现"，是设计上就不该有——
 * 加一个删除接口，审计链就失去了可追溯的意义。</p>
 */
@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    private final AdminLogService adminLogService;

    public AdminLogController(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<OperationLogVO>> list(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {

        AdminLogQuery query = new AdminLogQuery();
        query.setOperatorId(operatorId);
        query.setOperationType(operationType);
        query.setTargetType(targetType);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return ApiResponse.success(adminLogService.list(query));
    }
}
