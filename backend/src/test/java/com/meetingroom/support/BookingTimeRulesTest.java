package com.meetingroom.support;

import com.meetingroom.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约时间规则用例（FR-08 时间边界 ＋ FR-09 冲突判定）。
 *
 * <p>★ 第 9 周交付要求里点名的"首尾相接不冲突"正例在 {@link #边界_首尾相接不算冲突()}，
 * 它比反例更重要：判定式把 {@code <} 误写成 {@code <=} 时，反例照样通过，只有这条会红。</p>
 */
class BookingTimeRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(DAY, java.time.LocalTime.of(hour, minute));
    }

    private static LocalDateTime at(int hour, int minute, int second) {
        return LocalDateTime.of(DAY, java.time.LocalTime.of(hour, minute, second));
    }

    // ==================== 冲突判定：半开区间 [start, end) ====================

    @Test
    @DisplayName("正例：09:00-10:00 与 10:00-11:00 首尾相接，不算冲突")
    void 边界_首尾相接不算冲突() {
        // 新的 10:00-11:00 排在旧的 09:00-10:00 之后，10:00 这一刻旧预约已结束
        assertFalse(BookingTimeRules.conflicts(at(10, 0), at(11, 0), at(9, 0), at(10, 0)),
                "首尾相接被判成冲突，说明判定式误用了 <= 或 >=");
        // 反过来（新的排在旧的之前）同样不算冲突
        assertFalse(BookingTimeRules.conflicts(at(9, 0), at(10, 0), at(10, 0), at(11, 0)));
    }

    @Test
    @DisplayName("反例：真重叠 09:00-11:00 与 10:00-12:00 必须判为冲突")
    void 重叠_真重叠判冲突() {
        assertTrue(BookingTimeRules.conflicts(at(9, 0), at(11, 0), at(10, 0), at(12, 0)));
    }

    @Test
    @DisplayName("反例：新时段完全包住旧时段 / 被旧时段包住，都算冲突")
    void 重叠_包含关系判冲突() {
        assertTrue(BookingTimeRules.conflicts(at(9, 0), at(12, 0), at(10, 0), at(11, 0)));
        assertTrue(BookingTimeRules.conflicts(at(10, 0), at(11, 0), at(9, 0), at(12, 0)));
    }

    @Test
    @DisplayName("反例：两条完全相同的时间段算冲突")
    void 重叠_相同区间判冲突() {
        assertTrue(BookingTimeRules.conflicts(at(9, 0), at(10, 0), at(9, 0), at(10, 0)));
    }

    @Test
    @DisplayName("边界：只重叠 1 秒也算冲突；隔 1 分钟则不算")
    void 边界_一秒重叠与一分钟间隔() {
        assertTrue(BookingTimeRules.conflicts(at(9, 0), at(10, 0), at(9, 59, 59), at(11, 0)));
        assertFalse(BookingTimeRules.conflicts(at(9, 0), at(10, 0), at(10, 1), at(11, 0)));
    }

    @Test
    @DisplayName("反例：完全不搭界的两段不算冲突")
    void 重叠_完全分开不算冲突() {
        assertFalse(BookingTimeRules.conflicts(at(8, 0), at(9, 0), at(14, 0), at(15, 0)));
    }

    // ==================== 时间窗口校验（FR-08） ====================

    @Test
    @DisplayName("结束时间等于开始时间：拒绝")
    void 校验_结束不晚于开始() {
        assertEquals("结束时间必须晚于开始时间",
                BookingTimeRules.validateWindow(at(9, 0), at(9, 0), DAY, null));
        assertEquals("结束时间必须晚于开始时间",
                BookingTimeRules.validateWindow(at(10, 0), at(9, 0), DAY, null));
    }

    @Test
    @DisplayName("跨天：拒绝")
    void 校验_禁止跨天() {
        LocalDateTime start = at(23, 0);
        LocalDateTime end = LocalDateTime.of(DAY.plusDays(1), java.time.LocalTime.of(1, 0));
        assertEquals("预约不能跨天，且单次不能超过 8 小时",
                BookingTimeRules.validateWindow(start, end, DAY, null));
    }

    @Test
    @DisplayName("超过 8 小时：拒绝；正好 8 小时：放行")
    void 校验_八小时上限() {
        assertEquals("预约不能跨天，且单次不能超过 8 小时",
                BookingTimeRules.validateWindow(at(8, 0), at(17, 0), DAY, null));
        assertNull(BookingTimeRules.validateWindow(at(8, 0), at(16, 0), DAY, null));
    }

    @Test
    @DisplayName("预约日期与开始时间不一致：拒绝（防止 reserve_date 与 start_time 漂移）")
    void 校验_日期漂移() {
        assertEquals("预约日期与开始时间不一致，请重新选择",
                BookingTimeRules.validateWindow(at(9, 0), at(10, 0), DAY.plusDays(1), null));
    }

    @Test
    @DisplayName("开始时间早于当前时间：拒绝（FR-08）")
    void 校验_不得早于当前时间() {
        LocalDateTime now = at(12, 0);
        assertEquals("开始时间不能早于当前时间，请选择未来的时段",
                BookingTimeRules.validateWindow(at(9, 0), at(10, 0), DAY, now));
        assertNull(BookingTimeRules.validateWindow(at(13, 0), at(14, 0), DAY, now));
    }

    // ==================== 入参解析 ====================

    @Test
    @DisplayName("时间解析：完整 datetime、ISO 带 T、只给 HH:mm 三种写法都要认")
    void 解析_三种时间写法() {
        LocalDateTime expected = LocalDateTime.of(DAY, java.time.LocalTime.of(9, 0));
        assertEquals(expected, BookingTimeRules.parseTime("2026-10-27 09:00:00", DAY));
        assertEquals(expected, BookingTimeRules.parseTime("2026-10-27 09:00", DAY));
        assertEquals(expected, BookingTimeRules.parseTime("2026-10-27T09:00", DAY));
        assertEquals(expected, BookingTimeRules.parseTime("09:00", DAY));
        assertEquals(expected, BookingTimeRules.parseTime("09:00:00", DAY));
    }

    @Test
    @DisplayName("解析失败：给出面向使用者的中文提示，而不是 500")
    void 解析_格式错误提示是人话() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> BookingTimeRules.parseTime("明天上午九点", DAY));
        assertEquals(400, e.getCode());
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage().contains("时间格式不正确"));
    }
}
