package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.BookingRequest;
import com.meetingroom.dto.BookingVO;
import com.meetingroom.service.BookingService;
import com.meetingroom.support.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提交预约接口（草案 #5，第 9 周 虞业宸）。
 *
 * <pre>
 * POST /api/bookings
 * 请求体：{ "roomId":1, "date":"2026-10-27", "startTime":"2026-10-27 09:00:00",
 *          "endTime":"2026-10-27 10:00:00", "topic":"需求评审",
 *          "attendeeCount":8, "contact":"虞业宸", "purpose":"可选" }
 * 成功  ：{ "code":200, "message":"ok", "data":{ "id":8, "reservationNo":"MR20261027001", "status":"PENDING" } }
 * 冲突  ：{ "code":409, "message":"该会议室在此时间段已被占用",
 *          "data":{ "occupiedSlots":[ {"startTime":"10:00","endTime":"11:00"} ] } }
 * 未登录：{ "code":401, "message":"未登录", "data":null }
 * </pre>
 *
 * <p>★ 业务失败一律 HTTP 200、靠 {@code code} 区分——这是第 6 周定下的前后端约定，
 * 前端只判断一处，不必在 HTTP 状态码和业务码之间来回切换。</p>
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUserResolver currentUserResolver;

    public BookingController(BookingService bookingService, CurrentUserResolver currentUserResolver) {
        this.bookingService = bookingService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ApiResponse<BookingVO> create(@RequestBody BookingRequest request,
                                         HttpServletRequest httpRequest) {
        return ApiResponse.success(bookingService.create(request, currentUserResolver.resolve(httpRequest)));
    }
}
