package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.BookingRequest;
import com.meetingroom.dto.BookingVO;
import com.meetingroom.dto.ConflictPayload;
import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.dto.OccupiedSlotVO;
import com.meetingroom.entity.MeetingRoom;
import com.meetingroom.entity.Reservation;
import com.meetingroom.mapper.MeetingRoomMapper;
import com.meetingroom.mapper.ReservationMapper;
import com.meetingroom.support.BookingTimeRules;
import com.meetingroom.support.ReservationNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交预约服务（{@code POST /api/bookings}，草案 #5）。
 *
 * <p>一条链路做四件事，顺序不能换：</p>
 * <ol>
 *   <li><b>校验</b>FR-07 必填 + FR-08 时间边界（跨天、8 小时、end &gt; start、不得早于当前时间）；</li>
 *   <li><b>锁</b>先对会议室行 {@code SELECT ... FOR UPDATE}：同一间会议室的提交被串行化，
 *       避免两个用户同时订到同一时段（NF-07 并发不超订，幻读问题）；</li>
 *   <li><b>判</b>取当天已占用时段，用 {@link BookingTimeRules#conflicts} 逐条比对。
 *       判定式只此一处实现，与 409 响应里的 {@code occupiedSlots} 共用同一份查询结果
 *       （第 9 周计划 §3.1「冲突判定只写一遍」）；</li>
 *   <li><b>写</b>生成 13 位预约编号并落库，状态固定 PENDING（后续由读取推导 ONGOING／FINISHED）。</li>
 * </ol>
 *
 * <p>★ 为什么整个方法要 {@code @Transactional}：行锁在事务外会立刻释放，锁等于没加；
 * 且"查冲突—插入"必须原子，否则中间被另一个事务插入就白查了。</p>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String ROOM_ENABLED = "ENABLED";
    /** 编号撞唯一键时的重试次数 */
    private static final int MAX_ATTEMPTS = 3;

    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationMapper reservationMapper;
    private final MeetingRoomMapper meetingRoomMapper;

    public BookingService(ReservationMapper reservationMapper, MeetingRoomMapper meetingRoomMapper) {
        this.reservationMapper = reservationMapper;
        this.meetingRoomMapper = meetingRoomMapper;
    }

    /**
     * 提交一条预约。
     *
     * @param request 请求体
     * @param userId  当前登录用户ID（由 {@code CurrentUserResolver} 解析，null 视为未登录）
     * @return 新预约的主键、编号与状态
     */
    @Transactional(rollbackFor = Exception.class)
    public BookingVO create(BookingRequest request, Long userId) {
        if (userId == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录");
        }
        validateRequired(request);

        LocalDate date = BookingTimeRules.parseDate(request.getDate());
        LocalDateTime start = BookingTimeRules.parseTime(request.getStartTime(), date);
        LocalDateTime end = BookingTimeRules.parseTime(request.getEndTime(), date);

        String windowError = BookingTimeRules.validateWindow(start, end, date, LocalDateTime.now());
        if (windowError != null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, windowError);
        }

        // ② 锁会议室行：顺带拿到 status，停用判断放在锁内，避免"查完才发现刚被停用"
        MeetingRoom room = meetingRoomMapper.selectByIdForUpdate(request.getRoomId());
        if (room == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }
        if (!ROOM_ENABLED.equals(room.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "该会议室已停用，无法预约");
        }

        // ③ 冲突判定：半开区间 [start, end)，首尾相接不算冲突
        List<OccupiedSlotDTO> occupied = reservationMapper.selectOccupiedSlotsOfRoom(room.getId(), date);
        for (OccupiedSlotDTO slot : occupied) {
            if (BookingTimeRules.conflicts(start, end, slot.getStartTime(), slot.getEndTime())) {
                throw new BusinessException(ApiResponse.CODE_CONFLICT,
                        "该会议室在此时间段已被占用", new ConflictPayload(toSlotVOs(occupied)));
            }
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setRoomId(room.getId());
        reservation.setReserveDate(date);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setTopic(request.getTopic().trim());
        reservation.setAttendeeCount(request.getAttendeeCount());
        reservation.setContact(request.getContact().trim());
        reservation.setPurpose(blankToNull(request.getPurpose()));
        reservation.setStatus(STATUS_PENDING);

        insertWithUniqueNo(reservation, date);
        log.info("提交预约成功：no={}, userId={}, roomId={}, {} ~ {}",
                reservation.getReservationNo(), userId, room.getId(), start, end);
        return new BookingVO(reservation.getId(), reservation.getReservationNo(), reservation.getStatus());
    }

    /**
     * 生成编号并插入；万一编号撞唯一键（并发），重取最大值再试，最多 3 次。
     *
     * <p>行锁已经把同一间会议室串行化了，但"两间会议室同一天同时提交"仍可能取到同一个
     * 最大值——这是残留的窄窗口，用重试兜住，比让用户看到 500 更合适。</p>
     */
    private void insertWithUniqueNo(Reservation reservation, LocalDate date) {
        String prefix = ReservationNoGenerator.prefixOf(date);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String no = ReservationNoGenerator.next(prefix, reservationMapper.selectMaxReservationNo(prefix));
            reservation.setReservationNo(no);
            reservation.setId(null);
            try {
                reservationMapper.insert(reservation);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("预约编号 {} 撞唯一键，第 {} 次重试", no, attempt);
                if (attempt == MAX_ATTEMPTS) {
                    throw new BusinessException(ApiResponse.CODE_SERVER_ERROR,
                            "预约编号生成冲突，请重新提交");
                }
            }
        }
    }

    /** FR-07 必填项校验；提示语面向使用者，不是字段名（NF-10） */
    private void validateRequired(BookingRequest request) {
        if (request == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写预约信息");
        }
        if (request.getRoomId() == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择会议室");
        }
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写会议主题");
        }
        if (request.getAttendeeCount() == null || request.getAttendeeCount() <= 0) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "参会人数必须是大于 0 的整数");
        }
        if (request.getContact() == null || request.getContact().isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写联系人");
        }
        if (request.getDate() == null || request.getDate().isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择预约日期");
        }
    }

    /** 已占用时段转成 409 的载荷格式（只给时分，前端拼"09:00-10:00 已被占用"） */
    private List<OccupiedSlotVO> toSlotVOs(List<OccupiedSlotDTO> slots) {
        List<OccupiedSlotVO> result = new ArrayList<>();
        for (OccupiedSlotDTO slot : slots) {
            result.add(new OccupiedSlotVO(
                    slot.getStartTime().format(SLOT_FORMAT),
                    slot.getEndTime().format(SLOT_FORMAT)));
        }
        return result;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
