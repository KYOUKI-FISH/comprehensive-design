package com.meetingroom.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 时间序列化／反序列化配置（第 9 周）。
 *
 * <p><b>为什么不用 {@code spring.jackson.date-format}：</b>
 * 那个配置项只对 {@link java.util.Date} 生效，对 JSR-310 的
 * {@link LocalDateTime}／{@link LocalDate} <strong>不生效</strong>——
 * Spring Boot 会自动注册 {@code JavaTimeModule}，而它按 ISO-8601 解析，
 * 于是前端传 {@code "2026-11-02 14:30"} 会直接抛
 * {@code DateTimeParseException: Text '2026-11-02 14:30' could not be parsed at index 10}
 * （index 10 正是空格的位置）。这是第 9 周联调<strong>实际打出来的问题</strong>，不是推测。
 * </p>
 *
 * <p><b>本类的口径：</b>
 * <ul>
 *   <li>反序列化 {@link LocalDateTime}：<strong>同时接受</strong> {@code yyyy-MM-dd HH:mm}
 *       与 {@code yyyy-MM-dd HH:mm:ss}——前端表单提交的是分钟精度（{@code el-time-select}），
 *       而接口文档与脚本常用秒精度，两者都收，省得调用方猜格式；</li>
 *   <li>序列化 {@link LocalDateTime}：统一输出 {@code yyyy-MM-dd HH:mm:ss}；</li>
 *   <li>{@link LocalDate}：统一 {@code yyyy-MM-dd}。</li>
 * </ul>
 *
 * <p>★ 时间字段的语义仍是半开区间 {@code [start, end)}（FR-09），
 * 这里的格式约定与它无关，两者别混。</p>
 */
@Configuration
public class JacksonConfig {

    /** 日期时间：分钟精度（前端表单用的就是这个） */
    private static final DateTimeFormatter DATE_TIME_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 日期时间：秒精度（接口文档与脚本用的） */
    private static final DateTimeFormatter DATE_TIME_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 日期 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("meeting-room-time");
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_SECOND));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE));
            module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
            builder.modules(new JavaTimeModule(), module);
        };
    }

    /**
     * 宽松的 {@link LocalDateTime} 反序列化器：两种格式都收。
     */
    public static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String text = p.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();
            // 前端可能传带毫秒或带 T 的 ISO 形式，先统一成空格
            if (value.contains("T")) {
                value = value.replace('T', ' ');
            }
            // 去掉可能的毫秒部分（.123）
            int dot = value.indexOf('.');
            if (dot > 0) {
                value = value.substring(0, dot);
            }
            try {
                if (value.length() == 16) {
                    return LocalDateTime.parse(value, DATE_TIME_MINUTE);
                }
                return LocalDateTime.parse(value, DATE_TIME_SECOND);
            } catch (Exception e) {
                throw new IOException("时间格式无法解析：" + text
                        + "（应为 yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss）", e);
            }
        }
    }
}
