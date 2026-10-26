package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.BookingRequest;
import com.meetingroom.dto.ReservationCreateVO;
import com.meetingroom.service.BookingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约接口（第 9 周；第 8 周冻结接口清单第 5 条）。
 *
 * <pre>
 * POST /api/bookings
 * 请求体：{ "roomId": 1, "date": "2026-10-28", "startTime": "2026-10-28 09:00",
 *          "endTime": "2026-10-28 10:00", "topic": "项目评审会",
 *          "attendeeCount": 8, "contact": "张三", "purpose": "季度评审" }
 * 成功  ：{ "code": 200, "message": "ok",
 *          "data": { "id": 12, "reservationNo": "MR20261028012", "status": "PENDING" } }
 * 冲突  ：{ "code": 409, "message": "该会议室在此时间段已被占用",
 *          "data": { "occupiedSlots": [ { "startTime": "...", "endTime": "..." } ] } }
 * </pre>
 *
 * <p>★ 409 的 {@code data.occupiedSlots} 是 FR-10 的硬要求：
 * 系统不仅要说"冲突了"，还要告诉用户"被占用的是哪几个时段"。</p>
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ApiResponse<ReservationCreateVO> create(@RequestBody BookingRequest request) {
        return ApiResponse.success(bookingService.create(request));
    }
}
