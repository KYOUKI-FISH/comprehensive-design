package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.OccupancyVO;
import com.meetingroom.dto.RoomDetailVO;
import com.meetingroom.dto.RoomVO;
import com.meetingroom.service.MeetingRoomService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 会议室查询接口（第 6 周骨架 → 第 9 周扩展）。
 *
 * <pre>
 * GET /api/rooms
 *   ?location=逸夫楼       位置关键字（模糊匹配位置或名称），可不传
 *   &amp;minCapacity=10      最小容纳人数，可不传
 *   &amp;status=ENABLED      启用状态；查询页传 ENABLED 即可过滤掉停用会议室（FR-06）
 *   &amp;date=2026-10-12     传则返回每个会议室的当日已占用时段
 *
 * GET /api/rooms/{id}
 *   ?date=2026-10-12     传则附带该会议室当日已占用时段
 *   → 会议室详情（FR-05）；不存在时 404「会议室不存在」
 *
 * GET /api/rooms/{id}/occupancy
 *   ?date=2026-10-12     必填
 *   → 该会议室该日的占用时段（冻结清单第 4 条）
 * </pre>
 *
 * <p>★ 关于 {@code GET /api/rooms/{id}}：任务书第 9 周表 1 出现过这条路径，
 * 但第 7—8 周冻结的 14 条接口清单里没有它（清单第 4 条是 {@code /occupancy}）。
 * 本组采取<strong>组合供数</strong>而非新增接口：详情页的<strong>基本信息</strong>取自
 * {@code GET /api/rooms} 列表项，<strong>占用时段</strong>取自 {@code /occupancy}。
 * 详见 {@code docs/week09/01_接口变更记录.md} 决策 D1。</p>
 */
@RestController
@RequestMapping("/api/rooms")
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;

    public MeetingRoomController(MeetingRoomService meetingRoomService) {
        this.meetingRoomService = meetingRoomService;
    }

    @GetMapping
    public ApiResponse<List<RoomVO>> list(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(meetingRoomService.list(location, minCapacity, status, date));
    }

    /**
     * 会议室详情（第 9 周；FR-05、FR-06）。
     */
    @GetMapping("/{id}")
    public ApiResponse<RoomDetailVO> detail(@PathVariable Long id,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        RoomDetailVO vo = meetingRoomService.detail(id, date);
        if (vo == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }
        return ApiResponse.success(vo);
    }

    /**
     * 会议室占用时段（第 9 周；第 8 周冻结清单第 4 条，FR-17／FR-10 数据同源）。
     */
    @GetMapping("/{id}/occupancy")
    public ApiResponse<OccupancyVO> occupancy(@PathVariable Long id,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择预约日期");
        }
        OccupancyVO vo = meetingRoomService.occupancy(id, date);
        if (vo == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }
        return ApiResponse.success(vo);
    }
}
