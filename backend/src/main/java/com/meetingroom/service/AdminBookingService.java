package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.AdminBookingQuery;
import com.meetingroom.dto.PageResult;
import com.meetingroom.dto.ReservationDetailDTO;
import com.meetingroom.mapper.ReservationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端预约服务（接口 #9 全部预约、#10 强制取消异常预约）。
 *
 * <p>需求依据：FR-15 管理员查看全部预约、FR-16 强制取消异常预约、NF-14 操作留痕。</p>
 *
 * <p><b>★ 口径提醒（全篇适用）：</b>本项目的"管理员处理预约"是<strong>强制取消异常预约</strong>，
 * 不是逐条审批。方案 A（自由时间段预约）没有人工审核环节——预约提交即 PENDING，
 * 不需要管理员点头。所以这里没有"通过／驳回"，只有"取消并写明原因"。</p>
 *
 * <p><b>★ 本类不负责角色校验。</b>普通用户调 {@code /api/admin/**} 一律由
 * {@code AuthInterceptor} 拦成 403（NF-03）。权限判断放拦截器而不是 Service，
 * 是为了让"哪些路径要 ADMIN"只有一处定义；Service 若再判一次，两处口径迟早不一致。</p>
 */
@Service
public class AdminBookingService {

    private static final Logger log = LoggerFactory.getLogger(AdminBookingService.class);

    /** FR-16／接口清单 #10：取消原因不少于 5 个字符 */
    private static final int MIN_REASON_LENGTH = 5;
    /** 分页默认值：管理端页面一屏大约能放这么多 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final ReservationMapper reservationMapper;
    private final OperationLogService operationLogService;

    public AdminBookingService(ReservationMapper reservationMapper,
                               OperationLogService operationLogService) {
        this.reservationMapper = reservationMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 管理员查看全部预约（接口 #9，FR-15）。
     *
     * <p>筛选条件全部可空；分页不传时返回全部（管理端"先看全部再慢慢筛"的场景）。</p>
     */
    public PageResult<ReservationDetailDTO> list(AdminBookingQuery query) {
        if (query == null) {
            query = new AdminBookingQuery();
        }
        validateQuery(query);

        long total = reservationMapper.countAdminList(query);
        int pageNum = query.getPageNum() == null ? 1 : Math.max(1, query.getPageNum());
        int pageSize = normalizePageSize(query.getPageSize());
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);

        List<ReservationDetailDTO> rows = total == 0
                ? List.of()
                : reservationMapper.selectAdminList(query);
        return PageResult.of(total, pageNum, pageSize, rows);
    }

    /**
     * 强制取消一条异常预约（接口 #10，FR-16）。
     *
     * <p>四件事同一事务：① 校验原因；② 确认预约存在；③ 置为 CANCELLED 并写原因；
     * ④ 写一条 {@code BOOKING_FORCE_CANCEL} 操作日志。<b>③④ 必须同事务</b>——
     * 操作成功了但日志没写，NF-14 的审计链就断了，而这种缺失事后无法补救
     * （没人记得当时谁取消的）。</p>
     *
     * @param id     预约主键
     * @param reason 取消原因，必填、不少于 5 个字符
     * @return 取消后的预约详情
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservationDetailDTO forceCancel(Long id, String reason) {
        Long operatorId = CurrentUser.userId();
        String operatorUsername = CurrentUser.username();
        if (operatorId == null || operatorUsername == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录");
        }

        // ① FR-16：未填写原因时拒绝处理并提示
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.length() < MIN_REASON_LENGTH) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "请填写取消原因（不少于 " + MIN_REASON_LENGTH + " 个字符）");
        }

        // ② 先确认存在，好区分"不存在"(404) 与"已不是 PENDING"(400) 两种失败
        ReservationDetailDTO before = reservationMapper.selectDetailById(id);
        if (before == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "预约不存在");
        }
        if (STATUS_CANCELLED.equals(before.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "已取消的预约不能重复取消");
        }
        if (!STATUS_PENDING.equals(before.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "只有待开始的预约可以强制取消，该预约当前状态为 " + statusLabel(before.getStatus()));
        }

        // ③ 原子更新：where 带 status='PENDING'，并发下只有一个能改到行
        int affected = reservationMapper.updateStatusToCancelled(id, trimmedReason, LocalDateTime.now());
        if (affected == 0) {
            // 走到这里说明②与③之间被另一个管理员抢先改了状态
            throw new BusinessException(ApiResponse.CODE_CONFLICT,
                    "该预约状态已被他人变更，请刷新后重试");
        }

        // ④ NF-14：同事务写日志。★ 放在更新之后：日志写失败要连带回滚取消动作
        operationLogService.log(
                operatorId, operatorUsername,
                OperationLogService.BOOKING_FORCE_CANCEL,
                "BOOKING", id,
                toJson(STATUS_PENDING, before.getReservationNo(), null),
                toJson(STATUS_CANCELLED, before.getReservationNo(), trimmedReason),
                "管理员强制取消异常预约");

        log.info("强制取消预约：id={}, no={}, operator={}, reason={}",
                id, before.getReservationNo(), operatorUsername, trimmedReason);

        return reservationMapper.selectDetailById(id);
    }

    /** 参数合法性：状态枚举、日期区间 */
    private void validateQuery(AdminBookingQuery query) {
        String status = query.getStatus();
        if (status != null && !status.isBlank() && !isValidStatus(status)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "预约状态只能是 PENDING／ONGOING／FINISHED／CANCELLED");
        }
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "结束日期不能早于开始日期");
        }
    }

    /** 状态是否属于四态之一 */
    private boolean isValidStatus(String status) {
        return status != null && switch (status) {
            case "PENDING", "ONGOING", "FINISHED", "CANCELLED" -> true;
            default -> false;
        };
    }

    /** 状态中文名（NF-10：提示语给人看，不甩 PENDING 这种码值） */
    private String statusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PENDING" -> "待开始";
            case "ONGOING" -> "进行中";
            case "FINISHED" -> "已结束";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 拼一段合法 JSON 存进 before_data／after_data。
     *
     * <p>★ 手拼而不引 Jackson：这里只有三个定值字段，且 {@code reason} 来自用户输入，
     * 必须转义引号和反斜杠，否则会写出坏 JSON 让 JSON 列插入失败。
     * 引 Jackson 会更"正确"，但 OperationLogService 的接口收的是 String，
     * 为一个三字段小对象加依赖不值得；转义在 {@link #escape} 里做全。</p>
     */
    private String toJson(String status, String reservationNo, String cancelReason) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"status\":\"").append(escape(status)).append("\"");
        sb.append(",\"reservationNo\":\"").append(escape(reservationNo)).append("\"");
        if (cancelReason != null) {
            sb.append(",\"cancelReason\":\"").append(escape(cancelReason)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /** JSON 字符串转义：只处理会破坏 JSON 结构的字符 */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
