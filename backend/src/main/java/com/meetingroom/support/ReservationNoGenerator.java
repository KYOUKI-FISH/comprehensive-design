package com.meetingroom.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 预约编号生成 —— 第 10 周改造后的方案：<b>自增主键派生</b>。
 *
 * <p>规则：{@code MR + yyyyMMdd + 主键左补零至 3 位}，如
 * {@code MR20261028008}（id=8 那条 10-28 的预约）。</p>
 *
 * <p>★ 为什么第 10 周要改掉第 9 周的"取当日最大流水号 +1"：
 * 旧方案在并发下有撞唯一键 {@code uk_reservation_no} 的窗口——行锁只串行化了
 * <b>同一间会议室</b>的提交，两间会议室同一天并发提交仍可能取到同一个"最大值"，
 * 靠重试兜底（第 10 周计划 §2.3 攻坚点 2 点名此处，第 11 周 50 并发是硬指标）。
 * 主键天然唯一、且 {@code (id) → 编号} 是单射，<b>从根上没有撞键窗口</b>，
 * 不再需要"查最大值""重试"这两步。</p>
 *
 * <p><b>落库方式（三步，同一事务）：</b></p>
 * <ol>
 *   <li>先用 {@link #placeholder()} 生成 32 位随机占位串填进 {@code NOT NULL UNIQUE} 列；</li>
 *   <li>{@code INSERT}，{@code useGeneratedKeys} 回填自增主键；</li>
 *   <li>用 {@link #derive} 由 {@code (date, id)} 算出正式编号，一条 {@code UPDATE} 回填。</li>
 * </ol>
 *
 * <p>长度口径：主键不足 3 位左补零（课程场景 13 位），超过 999 自然变 4 位及以上，
 * {@code VARCHAR(32)} 容纳无压力；唯一性由"主键唯一 + 日期段定长"双保证。</p>
 */
public final class ReservationNoGenerator {

    /** 前缀 */
    public static final String PREFIX = "MR";

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private ReservationNoGenerator() {
    }

    /**
     * 由（预约日期，主键）派生正式编号。
     *
     * @param id 预约自增主键，必须 &gt; 0（插入成功回填后才有）
     */
    public static String derive(LocalDate date, long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("预约编号必须由回填的自增主键派生，当前 id=" + id);
        }
        return PREFIX + date.format(DAY) + String.format("%03d", id);
    }

    /**
     * 插入前的占位编号：32 位随机十六进制，恰好占满 {@code VARCHAR(32)}。
     *
     * <p>只为满足 {@code NOT NULL UNIQUE} 约束让插入成功，同一事务内立刻被
     * 正式编号覆盖；随机会在 2^122 空间里取值，占位期间撞键概率可忽略。</p>
     */
    public static String placeholder() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
