package com.meetingroom.support;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 预约时间规则 —— FR-08（时间边界）与 FR-09（冲突判定）的**唯一实现处**。
 *
 * <p>★ 为什么单独抽成一个"纯函数"类：这两条规则既要在业务里用，又要被自动化用例
 * 逐条覆盖（含"首尾相接不冲突"正例），抽出来才能不连数据库就测透；
 * Service 里只负责取数据、加锁、落库，不再重复写判定式。</p>
 *
 * <p><b>半开区间 [start, end)</b>：开始时刻属于本预约，结束时刻不属于。
 * 因此两条预约冲突的条件是</p>
 *
 * <pre>
 *   startNew &lt; endOld  且  endNew &gt; startOld
 * </pre>
 *
 * <p>用严格不等号（不是 {@code <=}）：09:00-10:00 与 10:00-11:00 首尾相接，
 * 10:00 这一刻前一单已经结束、后一单才开始，<b>不算冲突</b>。
 * 把 {@code <} 写成 {@code <=} 会把正常的相邻预约全判成冲突，
 * 这是本项目冲突检测最容易错的一处（第 8 周需求清单与任务书均单独点名）。</p>
 */
public final class BookingTimeRules {

    /** 单次预约最长分钟数：8 小时（FR-08） */
    public static final int MAX_MINUTES = 8 * 60;

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    private static final List<DateTimeFormatter> TIME_ONLY_FORMATS = List.of(
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm"));

    private BookingTimeRules() {
    }

    /**
     * 解析日期（yyyy-MM-dd）。
     *
     * @throws BusinessException 格式不对时抛 400，提示面向使用者
     */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择预约日期");
        }
        try {
            return LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "预约日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    /**
     * 解析时间。既接受完整的 {@code 2026-10-27 09:00:00}，也接受只给 {@code 09:00}
     * （此时用 {@code date} 补全日期）——两种写法前端都可能传。
     */
    public static LocalDateTime parseTime(String text, LocalDate date) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择开始时间和结束时间");
        }
        String value = text.trim();
        if (value.length() > 10) {
            for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
                try {
                    return LocalDateTime.parse(value, formatter);
                } catch (DateTimeParseException ignored) {
                    // 换下一种格式
                }
            }
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "时间格式不正确，应为 yyyy-MM-dd HH:mm:ss 或 09:00");
        }
        if (date == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择预约日期");
        }
        for (DateTimeFormatter formatter : TIME_ONLY_FORMATS) {
            try {
                return LocalDateTime.of(date, java.time.LocalTime.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // 换下一种格式
            }
        }
        throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                "时间格式不正确，应为 yyyy-MM-dd HH:mm:ss 或 09:00");
    }

    /**
     * 校验一条预约的时间窗口是否合法（FR-08）。
     *
     * @return {@code null} 表示合法；否则返回可直接展示给使用者的中文提示
     */
    public static String validateWindow(LocalDateTime start, LocalDateTime end,
                                        LocalDate date, LocalDateTime now) {
        if (start == null || end == null) {
            return "请选择开始时间和结束时间";
        }
        if (!end.isAfter(start)) {
            return "结束时间必须晚于开始时间";
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return "预约不能跨天，且单次不能超过 8 小时";
        }
        if (Duration.between(start, end).toMinutes() > MAX_MINUTES) {
            return "预约不能跨天，且单次不能超过 8 小时";
        }
        if (date != null && !date.equals(start.toLocalDate())) {
            return "预约日期与开始时间不一致，请重新选择";
        }
        // FR-08：开始时间不得早于当前时间。用 now 传入而不是在方法里取，方便用例构造"某一天"
        if (now != null && start.isBefore(now)) {
            return "开始时间不能早于当前时间，请选择未来的时段";
        }
        return null;
    }

    /**
     * 冲突判定：半开区间 {@code [startNew, endNew)} 与 {@code [startOld, endOld)} 是否重叠。
     *
     * <p>★ 这是本项目的核心判定式，严格使用 {@code <} 与 {@code >}：
     * 首尾相接（新开始 == 旧结束）返回 {@code false}，不算冲突。</p>
     */
    public static boolean conflicts(LocalDateTime startNew, LocalDateTime endNew,
                                    LocalDateTime startOld, LocalDateTime endOld) {
        if (startNew == null || endNew == null || startOld == null || endOld == null) {
            return false;
        }
        return startNew.isBefore(endOld) && endNew.isAfter(startOld);
    }
}
