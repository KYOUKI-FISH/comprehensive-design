package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.AdminLogQuery;
import com.meetingroom.dto.OperationLogVO;
import com.meetingroom.dto.PageResult;
import com.meetingroom.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 操作日志查询服务（接口 #13，NF-14 可审计性）。
 *
 * <p><b>★ 只读：</b>本类没有任何写方法。日志由各业务 Service 在自己的事务里写入
 * （{@link OperationLogService#log}），查询侧只负责按条件捞出来。
 * 需求清单 §#13 备注原话："只读不删，本接口不提供删除操作，避免审计链被破坏"。</p>
 *
 * <p><b>★ 权限：</b>普通用户访问 {@code /api/admin/logs} 由 {@code AuthInterceptor}
 * 拦成 403「无权访问该功能」——这是 NF-14 验证点之一
 * （"普通用户不可查看日志"），不在本类里另判一次。</p>
 */
@Service
public class AdminLogService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final OperationLogMapper operationLogMapper;

    public AdminLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 条件查询操作日志。
     *
     * @param query 条件（operatorId／operationType／targetType／startDate／endDate／分页）
     */
    public PageResult<OperationLogVO> list(AdminLogQuery query) {
        if (query == null) {
            query = new AdminLogQuery();
        }
        validateQuery(query);

        long total = operationLogMapper.countList(query);
        int pageNum = query.getPageNum() == null ? 1 : Math.max(1, query.getPageNum());
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(query.getPageSize(), MAX_PAGE_SIZE);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);

        List<OperationLogVO> rows = total == 0
                ? List.of()
                : operationLogMapper.selectList(query);
        rows.forEach(row -> {
            // ip 在建表脚本里没有对应列（第 8 周定稿口径），固定 null；
            // 此处显式置 null 是为了让前端拿到稳定字段，而不是"有时没有这个 key"
            row.setIp(null);
            row.setDetail(buildDetail(row));
        });
        return PageResult.of(total, pageNum, pageSize, rows);
    }

    /**
     * 拼人类可读的变更摘要。
     *
     * <p>★ 为什么不把 beforeData／afterData 的 JSON 原样丢给前端解析：
     * 日志页只需要一句"谁把什么改成了什么"，前端解析 JSON 要处理
     * "字段可能不存在""值是字符串还是对象"等一堆分支，而这里三个字段就够拼。
     * 原始 JSON 仍然一并返回，需要细节时前端可以展开。</p>
     */
    private String buildDetail(OperationLogVO row) {
        String typeLabel = operationTypeLabel(row.getOperationType());
        String targetLabel = targetTypeLabel(row.getTargetType());
        if (row.getRemark() != null && !row.getRemark().isBlank()) {
            return row.getRemark();
        }
        return typeLabel + targetLabel + (row.getTargetId() == null ? "" : "（ID " + row.getTargetId() + "）");
    }

    /** 9 种操作类型的中文名（NF-10：给使用者看的话，不甩码值） */
    private String operationTypeLabel(String type) {
        if (type == null) {
            return "未知操作";
        }
        return switch (type) {
            case "USER_CREATE" -> "新建账号";
            case "USER_DISABLE" -> "停用账号";
            case "USER_ENABLE" -> "启用账号";
            case "ROLE_CHANGE" -> "角色变更";
            case "ROOM_CREATE" -> "新增会议室";
            case "ROOM_UPDATE" -> "修改会议室";
            case "ROOM_DELETE" -> "删除会议室";
            case "ROOM_STATUS" -> "启停会议室";
            case "BOOKING_FORCE_CANCEL" -> "强制取消预约";
            default -> type;
        };
    }

    private String targetTypeLabel(String targetType) {
        if (targetType == null) {
            return "对象";
        }
        return switch (targetType) {
            case "USER" -> "用户";
            case "ROOM" -> "会议室";
            case "BOOKING" -> "预约";
            default -> targetType;
        };
    }

    /** 参数校验：操作类型／对象类型必须是枚举内的值，日期区间不能倒置 */
    private void validateQuery(AdminLogQuery query) {
        if (query.getOperationType() != null && !query.getOperationType().isBlank()
                && !OperationLogService.ALLOWED_TYPES.contains(query.getOperationType())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "不支持的操作类型：" + query.getOperationType());
        }
        if (query.getTargetType() != null && !query.getTargetType().isBlank()
                && !OperationLogService.ALLOWED_TARGETS.contains(query.getTargetType())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "不支持的操作对象类型：" + query.getTargetType()
                            + "（仅限 USER／ROOM／BOOKING）");
        }
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "结束日期不能早于开始日期");
        }
    }
}
