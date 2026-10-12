package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.RoomVO;
import com.meetingroom.service.MeetingRoomService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 会议室查询接口（环境验证通路之二）。
 *
 * <pre>
 * GET /api/rooms
 *   ?location=逸夫楼       位置关键字（模糊匹配位置或名称），可不传
 *   &amp;minCapacity=10      最小容纳人数，可不传
 *   &amp;status=ENABLED      启用状态；查询页传 ENABLED 即可过滤掉停用会议室（FR-06）
 *   &amp;date=2026-10-12     传则返回每个会议室的当日已占用时段
 * </pre>
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
}
