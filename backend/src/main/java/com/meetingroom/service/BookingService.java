package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.BookingItemVO;
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
import com.meetingroom.support.ReservationStatusPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 预约闭环服务（第 9/10 周 虞业宸）——提交、我的预约、修改、取消四条链路。
 *
 * <p><b>提交（{@code POST /api/bookings}）四步，顺序不能换：</b></p>
 * <ol>
 *   <li><b>校验</b>FR-07 必填 + FR-08 时间边界（跨天、8 小时、end &gt; start、不得早于当前时间）；</li>
 *   <li><b>锁</b>先对会议室行 {@code SELECT ... FOR UPDATE}：同一间会议室的提交被串行化，
 *       避免两个用户同时订到同一时段（NF-07 并发不超订，幻读问题）；</li>
 *   <li><b>判</b>取当天已占用时段，用 {@link BookingTimeRules#conflicts} 逐条比对。
 *       判定式只此一处实现，与 409 响应里的 {@code occupiedSlots} 共用同一份查询结果
 *       （第 9 周计划 §3.1「冲突判定只写一遍」）；</li>
 *   <li><b>写</b>占位编号插入 → 自增主键回填 → 派生正式编号回填（第 10 周改造，
 *       见 {@link ReservationNoGenerator} 类注释），状态固定 PENDING。</li>
 * </ol>
 *
 * <p><b>修改／取消（第 10 周接口 #7／#8）共用同一套准入：</b>
 * 本人 → 待开始（PENDING）→ 距开始超过 30 分钟（FR-12／FR-13），
 * 三道关都在<b>行锁内</b>判，判定口径集中在 {@link ReservationStatusPolicy}；
 * 修改还要在同一事务里对<b>新的时间段</b>重跑冲突检测并排除自身。</p>
 *
 * <p>★ 为什么整个方法要 {@code @Transactional}：行锁在事务外会立刻释放，锁等于没加；
 * 且"查冲突—插入"必须原子，否则中间被另一个事务插入就白查了。</p>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final String STATUS_PENDING = ReservationStatusPolicy.PENDING;
    private static final String STATUS_CANCELLED = ReservationStatusPolicy.CANCELLED;
    private static final String ROOM_ENABLED = "ENABLED";

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
        requireLogin(userId);
        validateRequired(request);

        ParsedTime time = parseAndValidateTime(request);
        MeetingRoom room = lockEnabledRoom(request.getRoomId());
        ensureNoConflict(room.getId(), time.date(), time.start(), time.end(), null);

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setRoomId(room.getId());
        reservation.setReserveDate(time.date());
        reservation.setStartTime(time.start());
        reservation.setEndTime(time.end());
        reservation.setTopic(request.getTopic().trim());
        reservation.setAttendeeCount(request.getAttendeeCount());
        reservation.setContact(request.getContact().trim());
        reservation.setPurpose(blankToNull(request.getPurpose()));
        reservation.setStatus(STATUS_PENDING);

        // 编号方案（第 10 周改造）：占位插入 → 主键回填 → 派生编号回填，无撞键窗口
        reservation.setReservationNo(ReservationNoGenerator.placeholder());
        reservationMapper.insert(reservation);
        String no = ReservationNoGenerator.derive(time.date(), reservation.getId());
        reservationMapper.updateReservationNo(reservation.getId(), no);
        reservation.setReservationNo(no);

        log.info("提交预约成功：no={}, userId={}, roomId={}, {} ~ {}",
                no, userId, room.getId(), time.start(), time.end());
        return new BookingVO(reservation.getId(), no, reservation.getStatus());
    }

    /**
     * 我的预约（接口 #6，FR-11）：只看本人的，状态是推导后的四态。
     *
     * <p>先做落库时校正（把已结束仍存 PENDING 的批量改 FINISHED，NF-08、不引入定时器），
     * 再查询，保证同一次响应里状态与库存值一致。</p>
     */
    @Transactional(readOnly = true)
    public List<BookingItemVO> mine(Long userId) {
        requireLogin(userId);
        reservationMapper.correctOverduePending();
        List<BookingItemVO> items = reservationMapper.selectMine(userId);
        LocalDateTime now = LocalDateTime.now();
        for (BookingItemVO item : items) {
            item.setStatus(ReservationStatusPolicy.derive(item.getStatus(),
                    item.getStartTime(), item.getEndTime(), now));
        }
        return items;
    }

    /**
     * 修改我的预约（接口 #7，FR-12）：改用途／时段／人数等，改后按新时段重跑冲突检测。
     *
     * <p>准入三道关（口径集中在 {@link ReservationStatusPolicy}）：
     * ①本人；②待开始；③距开始超过 30 分钟。
     * 冲突检测排除自身——自己原时段与新时段重叠不算冲突。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BookingVO modify(Long id, BookingRequest request, Long userId) {
        requireLogin(userId);
        if (id == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择要修改的预约");
        }
        Reservation reservation = reservationMapper.selectByIdForUpdate(id);
        if (reservation == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "预约不存在");
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(ApiResponse.CODE_FORBIDDEN, "只能修改本人提交的预约");
        }
        String reject = ReservationStatusPolicy.rejectModifyReason(
                reservation.getStatus(), reservation.getStartTime(), LocalDateTime.now());
        if (reject != null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, reject);
        }

        validateRequired(request);
        ParsedTime time = parseAndValidateTime(request);
        MeetingRoom room = lockEnabledRoom(request.getRoomId());
        ensureNoConflict(room.getId(), time.date(), time.start(), time.end(), id);

        reservation.setRoomId(room.getId());
        reservation.setReserveDate(time.date());
        reservation.setStartTime(time.start());
        reservation.setEndTime(time.end());
        reservation.setTopic(request.getTopic().trim());
        reservation.setAttendeeCount(request.getAttendeeCount());
        reservation.setContact(request.getContact().trim());
        reservation.setPurpose(blankToNull(request.getPurpose()));
        reservationMapper.updateBookingFields(reservation);

        log.info("修改预约成功：no={}, userId={}, 新时段 {} ~ {}",
                reservation.getReservationNo(), userId, time.start(), time.end());
        return new BookingVO(id, reservation.getReservationNo(), reservation.getStatus());
    }

    /**
     * 取消我的预约（接口 #8，FR-13）：状态改 CANCELLED，时段立即释放（NF-08）。
     *
     * <p>取消原因选填（"必填原因"是 FR-16 对管理员强制取消的要求，不是这里）。
     * 不写 {@code operation_log}：NF-14 只记管理员敏感操作，与第 9 周口径一致。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BookingVO cancel(Long id, String reason, Long userId) {
        requireLogin(userId);
        if (id == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择要取消的预约");
        }
        Reservation reservation = reservationMapper.selectByIdForUpdate(id);
        if (reservation == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "预约不存在");
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new BusinessException(ApiResponse.CODE_FORBIDDEN, "只能取消本人提交的预约");
        }
        String reject = ReservationStatusPolicy.rejectCancelReason(
                reservation.getStatus(), reservation.getStartTime(), LocalDateTime.now());
        if (reject != null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, reject);
        }

        String trimmedReason = (reason == null || reason.isBlank()) ? null : reason.trim();
        reservationMapper.updateStatus(id, STATUS_CANCELLED, trimmedReason);

        log.info("取消预约成功：no={}, userId={}, reason={}",
                reservation.getReservationNo(), userId, trimmedReason);
        return new BookingVO(id, reservation.getReservationNo(), STATUS_CANCELLED);
    }

    // ==================== 内部步骤 ====================

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录");
        }
    }

    /** 解析并校验时间（FR-08），返回日期与起止时刻 */
    private ParsedTime parseAndValidateTime(BookingRequest request) {
        LocalDate date = BookingTimeRules.parseDate(request.getDate());
        LocalDateTime start = BookingTimeRules.parseTime(request.getStartTime(), date);
        LocalDateTime end = BookingTimeRules.parseTime(request.getEndTime(), date);
        String windowError = BookingTimeRules.validateWindow(start, end, date, LocalDateTime.now());
        if (windowError != null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, windowError);
        }
        return new ParsedTime(date, start, end);
    }

    /**
     * 锁会议室行并确认启用（FR-06）。
     * 停用判断放在锁内，避免"查完才发现刚被停用"。
     */
    private MeetingRoom lockEnabledRoom(Long roomId) {
        if (roomId == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择会议室");
        }
        MeetingRoom room = meetingRoomMapper.selectByIdForUpdate(roomId);
        if (room == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }
        if (!ROOM_ENABLED.equals(room.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "该会议室已停用，无法预约");
        }
        return room;
    }

    /**
     * 冲突判定（FR-09）：半开区间 [start, end)，首尾相接不算冲突。
     * 409 时把<b>同一份</b>查询结果带回给前端（FR-10），判定式只在
     * {@code BookingTimeRules.conflicts} 一处实现。
     *
     * @param excludeId 修改场景排除自身，其余传 {@code null}
     */
    private void ensureNoConflict(Long roomId, LocalDate date,
                                  LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<OccupiedSlotDTO> occupied = reservationMapper.selectOccupiedSlotsOfRoom(roomId, date, excludeId);
        for (OccupiedSlotDTO slot : occupied) {
            if (BookingTimeRules.conflicts(start, end, slot.getStartTime(), slot.getEndTime())) {
                throw new BusinessException(ApiResponse.CODE_CONFLICT,
                        "该会议室在此时间段已被占用", new ConflictPayload(toSlotVOs(occupied)));
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

    /** 解析后的时间三元组 */
    private record ParsedTime(LocalDate date, LocalDateTime start, LocalDateTime end) {
    }
}
