package com.meetingroom.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约编号生成用例：MR + yyyyMMdd + 3 位流水号 = 13 位字符。
 *
 * <p>13 位这个口径同时出现在第 8 周设计文档、建表脚本注释与 7 条种子数据里，
 * 这里用正则把长度钉死，避免第 10 周改代码时悄悄漂移。</p>
 */
class ReservationNoGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);
    private static final String PREFIX = "MR20261027";

    @Test
    @DisplayName("前缀格式：MR + 8 位日期")
    void 前缀() {
        assertEquals(PREFIX, ReservationNoGenerator.prefixOf(DAY));
    }

    @Test
    @DisplayName("当天第一条：流水号从 001 起，总长 13 位")
    void 当天第一条() {
        String no = ReservationNoGenerator.next(PREFIX, null);
        assertEquals("MR20261027001", no);
        assertEquals(13, no.length());
        assertTrue(no.matches("MR\\d{8}\\d{3}"));
    }

    @Test
    @DisplayName("递增：009 之后是 010（补零不能丢）")
    void 递增补零() {
        assertEquals("MR20261027010", ReservationNoGenerator.next(PREFIX, "MR20261027009"));
        assertEquals("MR20261027100", ReservationNoGenerator.next(PREFIX, "MR20261027099"));
    }

    @Test
    @DisplayName("跨过 999：继续可用（自然变 4 位），不报错、不重复")
    void 超过三位流水号() {
        String no = ReservationNoGenerator.next(PREFIX, "MR20261027999");
        assertEquals("MR202610271000", no);
    }

    @Test
    @DisplayName("存量编号格式异常时退回 001，而不是抛异常")
    void 异常存量编号() {
        assertEquals("MR20261027001", ReservationNoGenerator.next(PREFIX, "MR20261027ABC"));
    }
}
