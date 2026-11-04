package com.meetingroom.support;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 预约状态四态的**唯一判定处**（NF-08）——读取时推导、落库时校正、修改/取消准入，
 * 三件事都只调这里，不许在业务代码里散写时间比较。
 *
 * <p><b>为什么"ONGOING／FINISHED"不入库、靠推导：</b>
 * 状态随真实时间流逝自动变化，而需求清单里没有"定时任务"功能（第 10 周计划 §2.3
 * 明确"不引入定时器"）。入库的只有三态锚点：PENDING（提交时写入）、
 * CANCELLED（取消时写入）、FINISHED（读取发现已过期时<b>顺手校正</b>落库）；
 * ONGOING 永远在读取那一刻推导，不落库。</p>
 *
 * <p><b>推导口径（半开区间 [start, end)，与 FR-09 冲突判定同一套区间语义）：</b></p>
 *
 * <pre>
 *   now &lt;  start          → PENDING   待开始
 *   start ≤ now &lt; end     → ONGOING   进行中（含开始时刻，不含结束时刻）
 *   now ≥ end             → FINISHED  已结束
 *   CANCELLED             → CANCELLED 已取消（用户态冻结，不再随时间变）
 * </pre>
 *
 * <p><b>30 分钟口径（FR-12／FR-13）：</b>需求清单 V1.1 原文"距会议开始<b>超过</b> 30 分钟
 * 才可修改或取消"——是严格大于：正好剩 30 分 00 秒<b>不允许</b>，剩 30 分 01 秒才允许。
 * 已开始的预约（ONGOING／FINISHED）天然被挡在 PENDING 准入之外，无需另判。</p>
 */
public final class ReservationStatusPolicy {

    public static final String PENDING = "PENDING";
    public static final String ONGOING = "ONGOING";
    public static final String FINISHED = "FINISHED";
    public static final String CANCELLED = "CANCELLED";

    /** 修改／取消的提前量：距开始须超过 30 分钟（FR-12、FR-13） */
    public static final int MIN_LEAD_SECONDS = 30 * 60;

    private ReservationStatusPolicy() {
    }

    /**
     * 读取时的对外展示状态。
     *
     * @param storedStatus 库里存的状态（PENDING／FINISHED／CANCELLED；ONGOING 不入库，传了也按区间推导兜底）
     */
    public static String derive(String storedStatus, LocalDateTime start,
                                LocalDateTime end, LocalDateTime now) {
        if (CANCELLED.equals(storedStatus)) {
            return CANCELLED;
        }
        if (now.isBefore(start)) {
            return PENDING;
        }
        if (now.isBefore(end)) {
            return ONGOING;
        }
        return FINISHED;
    }

    /**
     * 该预约当前是否允许修改（FR-12）：必须是待开始的预约，且距开始超过 30 分钟。
     *
     * @return {@code null} 表示允许；否则返回可直接展示的拒绝原因
     */
    public static String rejectModifyReason(String storedStatus, LocalDateTime start, LocalDateTime now) {
        return rejectChangeReason(storedStatus, start, now, "修改");
    }

    /**
     * 该预约当前是否允许取消（FR-13）：规则与修改一致。
     *
     * @return {@code null} 表示允许；否则返回可直接展示的拒绝原因
     */
    public static String rejectCancelReason(String storedStatus, LocalDateTime start, LocalDateTime now) {
        return rejectChangeReason(storedStatus, start, now, "取消");
    }

    private static String rejectChangeReason(String storedStatus, LocalDateTime start,
                                             LocalDateTime now, String action) {
        if (CANCELLED.equals(storedStatus)) {
            return "该预约已取消，无需再" + action;
        }
        if (!now.isBefore(start)) {
            return "该预约已开始或已结束，不能" + action;
        }
        if (Duration.between(now, start).getSeconds() <= MIN_LEAD_SECONDS) {
            return "距会议开始不足 30 分钟，不能" + action;
        }
        return null;
    }
}
