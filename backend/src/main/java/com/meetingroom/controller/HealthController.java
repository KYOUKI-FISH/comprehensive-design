package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口 —— 给"环境能跑、库能连"提供一条可直接验证的证据。
 *
 * <pre>
 * GET /api/health
 * → { "code": 200, "message": "ok",
 *     "data": { "application": "meeting-room-backend", "timestamp": "2026-10-12T17:20:00",
 *               "database": "UP" } }
 * </pre>
 *
 * <p>数据库连不上时不抛 500，而是如实返回 {@code "database": "DOWN"} 与错误信息，
 * 便于按 README「常见问题」逐条排查，而不是只看到一片红。</p>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "meeting-room-backend");
        data.put("timestamp", LocalDateTime.now().toString());
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("database", "UP");
        } catch (Exception e) {
            data.put("database", "DOWN");
            data.put("databaseError", e.getMessage());
        }
        return ApiResponse.success(data);
    }
}
