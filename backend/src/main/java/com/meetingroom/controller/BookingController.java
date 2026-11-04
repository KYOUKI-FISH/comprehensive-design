package com.meetingroom.controller;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.dto.BookingItemVO;
import com.meetingroom.dto.BookingRequest;
import com.meetingroom.dto.BookingVO;
import com.meetingroom.dto.CancelRequest;
import com.meetingroom.service.BookingService;
import com.meetingroom.support.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预约闭环接口（草案 #5 ＋ 第 10 周接口 #6/#7/#8，虞业宸）。
 *
 * <pre>
 * ① POST /api/bookings —— 提交预约（FR-07~FR-10）
 * 请求体：{ "roomId":1, "date":"2026-10-27", "startTime":"2026-10-27 09:00:00",
 *          "endTime":"2026-10-27 10:00:00", "topic":"需求评审",
 *          "attendeeCount":8, "contact":"虞业宸", "purpose":"可选" }
 * 成功  ：{ "code":200, "message":"ok", "data":{ "id":8, "reservationNo":"MR20261027008", "status":"PENDING" } }
 * 冲突  ：{ "code":409, "message":"该会议室在此时间段已被占用",
 *          "data":{ "occupiedSlots":[ {"startTime":"10:00","endTime":"11:00"} ] } }
 * 未登录：{ "code":401, "message":"未登录", "data":null }
 *
 * ② GET /api/bookings/mine —— 我的预约（FR-11、NF-08 四态展示）
 * 成功  ：{ "code":200, "data":[ { "id":8, "reservationNo":"MR20261027008",
 *          "roomName":"逸夫楼 A101", "date":"2026-10-27", "startTime":"...",
 *          "endTime":"...", "topic":"...", "status":"PENDING", ... } ] }
 * ★ status 是读取时推导的四态：PENDING／ONGOING／FINISHED／CANCELLED
 *
 * ③ PUT /api/bookings/{id} —— 修改我的预约（FR-12：本人、待开始、距开始超 30 分钟）
 * 请求体与 ① 相同（含新的时间），改后按新时段重跑冲突检测并排除自身
 * 非本人 403；已开始／已取消／不足 30 分钟 → 400 并给中文原因
 *
 * ④ DELETE /api/bookings/{id} —— 取消我的预约（FR-13：释放时段；原因选填）
 * 请求体（可选）：{ "reason":"时间冲突了" }
 * 成功  ：{ "code":200, "data":{ "id":8, "status":"CANCELLED" } }
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

    @GetMapping("/mine")
    public ApiResponse<List<BookingItemVO>> mine(HttpServletRequest httpRequest) {
        return ApiResponse.success(bookingService.mine(currentUserResolver.resolve(httpRequest)));
    }

    @PutMapping("/{id}")
    public ApiResponse<BookingVO> modify(@PathVariable Long id,
                                         @RequestBody BookingRequest request,
                                         HttpServletRequest httpRequest) {
        return ApiResponse.success(
                bookingService.modify(id, request, currentUserResolver.resolve(httpRequest)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<BookingVO> cancel(@PathVariable Long id,
                                         @RequestBody(required = false) CancelRequest request,
                                         HttpServletRequest httpRequest) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.success(
                bookingService.cancel(id, reason, currentUserResolver.resolve(httpRequest)));
    }
}
