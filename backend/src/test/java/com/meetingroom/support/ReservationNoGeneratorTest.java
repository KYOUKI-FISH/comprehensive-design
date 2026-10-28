package com.meetingroom.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约编号生成用例 —— 第 10 周改造后的<b>自增主键派生</b>方案。
 *
 * <p>规则 {@code MR + yyyyMMdd + 左补零主键}：主键天然唯一，编号从根上无撞键窗口；
 * 用例钉住格式与派生关系，防止后续再往"当日流水号"方向漂移。</p>
 */
class ReservationNoGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    @Test
    @DisplayName("派生：MR + 8 位日期 + 3 位补零主键，13 位")
    void 派生格式() {
        String no = ReservationNoGenerator.derive(DAY, 8L);
        assertEquals("MR20261028008", no);
        assertEquals(13, no.length());
        assertTrue(no.matches("MR\\d{8}\\d{3,}"));
    }

    @Test
    @DisplayName("主键不同 → 编号必不相同（单射，唯一性的根）")
    void 主键单射() {
        assertNotEquals(ReservationNoGenerator.derive(DAY, 1L),
                ReservationNoGenerator.derive(DAY, 2L));
    }

    @Test
    @DisplayName("主键超过 999：自然变 4 位，VARCHAR(32) 内不报错")
    void 主键超三位() {
        String no = ReservationNoGenerator.derive(DAY, 1234L);
        assertEquals("MR202610281234", no);
        assertEquals(14, no.length());
    }

    @Test
    @DisplayName("占位编号：32 位十六进制，恰好占满 VARCHAR(32)，两次调用互不相同")
    void 占位编号() {
        String p1 = ReservationNoGenerator.placeholder();
        String p2 = ReservationNoGenerator.placeholder();
        assertEquals(32, p1.length());
        assertTrue(p1.matches("[0-9a-f]{32}"));
        assertNotEquals(p1, p2);
    }

    @Test
    @DisplayName("主键缺失（未回填）时派生必须失败，不能静默生成坏编号")
    void 主键缺失拒绝() {
        assertThrows(IllegalArgumentException.class, () -> ReservationNoGenerator.derive(DAY, 0L));
        assertThrows(IllegalArgumentException.class, () -> ReservationNoGenerator.derive(DAY, -1L));
    }
}
