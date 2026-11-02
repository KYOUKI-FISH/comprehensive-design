package com.meetingroom.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 预约编号生成 —— 规则 {@code MR + yyyyMMdd + 3 位流水号}，共 13 位字符。
 *
 * <p>例如 2026-10-27 的第一条预约：{@code MR20261027001}。</p>
 *
 * <p>★ 为什么规则要抽成一个纯函数：编号是**对外展示的业务主键**，13 位这个长度
 * 在第 8 周文档、建表脚本注释、7 条种子数据里都出现过，写漂移了测试和联调都会对不上；
 * 抽出来后用例可以逐条断言格式，Service 只负责"取当天最大值 → 调它 → 落库"。</p>
 *
 * <p>★ 流水号超过 999 时会自然变成 4 位（{@code %03d} 只是最少补 3 位），
 * 编号仍然唯一、按字典序排列也仍然有序；一天上千条预约在课程场景不会出现，
 * 这里选择"不报错、继续可预约"，而不是给演示造一个死路。</p>
 */
public final class ReservationNoGenerator {

    /** 前缀 */
    public static final String PREFIX = "MR";

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private ReservationNoGenerator() {
    }

    /** 某天的编号前缀，如 {@code MR20261027} */
    public static String prefixOf(LocalDate date) {
        return PREFIX + date.format(DAY);
    }

    /**
     * 由当天已用的最大编号推出下一个编号。
     *
     * @param prefix  {@link #prefixOf} 的结果
     * @param maxNo   当天已用的最大编号，当天还没有预约时传 {@code null}
     */
    public static String next(String prefix, String maxNo) {
        int serial = 1;
        if (maxNo != null && maxNo.length() > prefix.length()) {
            try {
                serial = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                // 存量编号格式异常时退回从 1 开始，唯一性仍由 uk_reservation_no 兜底
                serial = 1;
            }
        }
        return prefix + String.format("%03d", serial);
    }
}
