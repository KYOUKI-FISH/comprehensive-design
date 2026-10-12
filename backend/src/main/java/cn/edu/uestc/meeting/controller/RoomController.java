package cn.edu.uestc.meeting.controller;

import cn.edu.uestc.meeting.common.ApiResult;
import cn.edu.uestc.meeting.entity.MeetingRoom;
import cn.edu.uestc.meeting.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会议室查询接口（骨架级）—— P2 查询会议室 / FR-05 按条件查询、FR-06 只显示启用中的。
 *
 * <pre>
 * GET /api/rooms
 * GET /api/rooms?keyword=主楼
 * GET /api/rooms?minCapacity=10
 * 出参：{ "code":200, "message":"成功", "data":[ { "id":1, "name":"主楼 A301", ... } ] }
 * </pre>
 *
 * <p>返回体直接使用 {@link MeetingRoom} 实体（无敏感字段）。
 * 第 7 周接口设计时若出参需要裁剪或补充"当日可用时段"等派生字段，再拆 VO。
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ApiResult<List<MeetingRoom>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer minCapacity) {
        return ApiResult.ok(roomService.listEnabled(keyword, minCapacity));
    }
}
