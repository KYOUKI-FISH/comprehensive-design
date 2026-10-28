package com.meetingroom.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 状态四态推导与修改/取消准入用例（NF-08 四态、FR-12／FR-13 的 30 分钟口径）。
 *
 * <p>边界都钉在这里：开始那一刻算 ONGOING（半开区间左闭）、结束那一刻算 FINISHED
 * （右开）、正好剩 30 分钟不允许改/取消（需求原文"超过 30 分钟"是严格大于）。</p>
 */
class ReservationStatusPolicyTest {

    /** 一场 09:00-11:00 的预约 */
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 28, 11, 0);

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 28, hour, minute);
    }

    // ==================== 四态推导 ====================

    @Test
    @DisplayName("开始前 → PENDING")
    void 待开始() {
        assertEquals("PENDING", ReservationStatusPolicy.derive("PENDING", START, END, at(8, 59)));
    }

    @Test
    @DisplayName("开始那一刻（半开区间左闭）→ ONGOING")
    void 开始时刻算进行中() {
        assertEquals("ONGOING", ReservationStatusPolicy.derive("PENDING", START, END, at(9, 0)));
    }

    @Test
    @DisplayName("进行中段 → ONGOING")
    void 进行中() {
        assertEquals("ONGOING", ReservationStatusPolicy.derive("PENDING", START, END, at(10, 0)));
    }

    @Test
    @DisplayName("结束那一刻（右开）→ FINISHED，不是 ONGOING")
    void 结束时刻算已结束() {
        assertEquals("FINISHED", ReservationStatusPolicy.derive("PENDING", START, END, at(11, 0)));
    }

    @Test
    @DisplayName("已取消的预约冻结在 CANCELLED，不随时间变成 FINISHED")
    void 已取消冻结() {
        assertEquals("CANCELLED", ReservationStatusPolicy.derive("CANCELLED", START, END, at(8, 0)));
        assertEquals("CANCELLED", ReservationStatusPolicy.derive("CANCELLED", START, END, at(12, 0)));
    }

    // ==================== 修改准入（FR-12） ====================

    @Test
    @DisplayName("距开始超过 30 分钟 → 允许修改")
    void 提前充足可修改() {
        assertNull(ReservationStatusPolicy.rejectModifyReason("PENDING", START, at(8, 0)));
    }

    @Test
    @DisplayName("正好剩 30 分钟 → 不允许（『超过』是严格大于）")
    void 恰好三十分钟不可修改() {
        String reason = ReservationStatusPolicy.rejectModifyReason("PENDING", START, at(8, 30));
        assertNotNull(reason);
        assertEquals("距会议开始不足 30 分钟，不能修改", reason);
    }

    @Test
    @DisplayName("已开始 → 不允许修改")
    void 已开始不可修改() {
        String reason = ReservationStatusPolicy.rejectModifyReason("PENDING", START, at(9, 30));
        assertEquals("该预约已开始或已结束，不能修改", reason);
    }

    @Test
    @DisplayName("已取消 → 不需要再修改")
    void 已取消不可修改() {
        assertEquals("该预约已取消，无需再修改",
                ReservationStatusPolicy.rejectModifyReason("CANCELLED", START, at(8, 0)));
    }

    // ==================== 取消准入（FR-13） ====================

    @Test
    @DisplayName("距开始超过 30 分钟 → 允许取消")
    void 提前充足可取消() {
        assertNull(ReservationStatusPolicy.rejectCancelReason("PENDING", START, at(8, 0)));
    }

    @Test
    @DisplayName("剩 29 分 59 秒 → 不允许取消")
    void 不足三十分钟不可取消() {
        String reason = ReservationStatusPolicy.rejectCancelReason(
                "PENDING", START, START.minusSeconds(29 * 60 + 59));
        assertEquals("距会议开始不足 30 分钟，不能取消", reason);
    }

    @Test
    @DisplayName("恰好剩 30 分钟 → 不允许取消（与修改同口径）")
    void 恰好三十分钟不可取消() {
        assertNotNull(ReservationStatusPolicy.rejectCancelReason("PENDING", START, at(8, 30)));
    }

    @Test
    @DisplayName("已取消 → 无需再取消")
    void 已取消不可再取消() {
        assertEquals("该预约已取消，无需再取消",
                ReservationStatusPolicy.rejectCancelReason("CANCELLED", START, at(8, 0)));
    }
}
