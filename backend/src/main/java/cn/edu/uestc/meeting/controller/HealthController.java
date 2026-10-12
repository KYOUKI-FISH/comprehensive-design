package cn.edu.uestc.meeting.controller;

import cn.edu.uestc.meeting.common.ApiResult;
import cn.edu.uestc.meeting.mapper.MeetingRoomMapper;
import cn.edu.uestc.meeting.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动自检接口（骨架级）。
 *
 * <pre>
 * GET /api/health
 * 出参：{ "code":200, "message":"成功", "data":{ "status":"UP", "database":"connected",
 *         "userCount":2, "roomCount":4 } }
 * </pre>
 *
 * <p>用途：现场演示"系统能启动"（质量与交付要求 QR-03）时，一条命令即可证明
 * <b>进程起来了 + 数据库连上了 + 表里有种子数据</b>，不必先走一遍登录。
 * 若数据库连不上，本接口会返回 500 —— 这正是它该有的表现。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final UserMapper userMapper;
    private final MeetingRoomMapper meetingRoomMapper;

    public HealthController(UserMapper userMapper, MeetingRoomMapper meetingRoomMapper) {
        this.userMapper = userMapper;
        this.meetingRoomMapper = meetingRoomMapper;
    }

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("database", "connected");
        data.put("userCount", userMapper.countByStatus(1));
        data.put("roomCount", meetingRoomMapper.countAll());
        return ApiResult.ok(data);
    }
}
