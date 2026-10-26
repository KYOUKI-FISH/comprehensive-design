package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.BookingRequest;
import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.dto.OccupiedSlotVO;
import com.meetingroom.dto.ReservationCreateVO;
import com.meetingroom.entity.MeetingRoom;
import com.meetingroom.entity.Reservation;
import com.meetingroom.mapper.MeetingRoomMapper;
import com.meetingroom.mapper.ReservationMapper;
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
 * 预约服务（第 9 周；对应 FR-07 提交预约、FR-08 时间规则、FR-09 冲突检测、
 * FR-10 冲突提示、FR-06 停用会议室、NF-07 并发防超卖）。
 *
 * <h3>★ 半开区间冲突判定（本项目最容易写错的地方）</h3>
 * 冲突条件<strong>只能</strong>是：
 * <pre>
 *   start_new &lt; end_old  AND  end_new &gt; start_old
 * </pre>
 * 写成 {@code <=} 会把"首尾相接"判成冲突——而 FR-09 明确规定首尾相接<strong>不算</strong>冲突
 * （09:00—10:00 与 10:00—11:00 可以同时预订）。第 12 周预答辩会重点抽查这一条。
 *
 * <h3>★ 并发防超卖：事务 ＋ 锁会议室行</h3>
 * 三步<strong>必须</strong>在同一个事务里：
 * <ol>
 *   <li>{@code SELECT ... FROM meeting_room WHERE id=? FOR UPDATE} —— 锁会议室行；</li>
 *   <li>查该会议室当日的占用行，做半开区间判定；</li>
 *   <li>插入预约记录。</li>
 * </ol>
 * <b>为什么锁 meeting_room 行而不是 reservation 行：</b>并发提交时，冲突方那条预约
 * <strong>还不存在</strong>，锁不到它；会议室行一定存在，锁它能把"同一会议室"的并发请求串行化。
 * <b>只加事务不加锁是不够的</b>——"先查后插"中间的时间差会让两个人同时通过。
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** 预约编号日期部分格式 */
    private static final DateTimeFormatter NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 单次预约时长上限：8 小时（FR-08） */
    private static final int MAX_MINUTES = 480;

    /** 会议室状态：停用（FR-06 停用会议室不可被预约） */
    private static final String ROOM_STATUS_DISABLED = "DISABLED";

    private final ReservationMapper reservationMapper;
    private final MeetingRoomMapper meetingRoomMapper;

    public BookingService(ReservationMapper reservationMapper, MeetingRoomMapper meetingRoomMapper) {
        this.reservationMapper = reservationMapper;
        this.meetingRoomMapper = meetingRoomMapper;
    }

    /**
     * 提交预约（第 9 周核心；FR-07）。
     *
     * @param request 预约请求
     * @return 预约ID、编号与状态；冲突时抛出带 {@code occupiedSlots} 的 409
     */
    @Transactional(rollbackFor = Exception.class)
    public ReservationCreateVO create(BookingRequest request) {
        // ---------- 第一步：参数与业务规则校验（FR-07、FR-08、FR-06）----------
        validateRequired(request);
        LocalDateTime start = request.getStartTime();
        LocalDateTime end = request.getEndTime();
        validateTimeRule(start, end);
        validateRoomBookable(request.getRoomId());

        LocalDate reserveDate = start.toLocalDate();

        // ---------- 第二步：锁会议室行（★ 并发防超卖的关键）----------
        // 与后面的冲突查询、INSERT 必须在同一事务里，否则锁一放就失效。
        MeetingRoom locked = meetingRoomMapper.lockById(request.getRoomId());
        if (locked == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }
        if (ROOM_STATUS_DISABLED.equals(locked.getStatus())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "该会议室已停用，无法预约");
        }

        // ---------- 第三步：半开区间冲突检测（FR-09）----------
        // ★ 必须用【锁定读】版本（FOR UPDATE）：REPEATABLE READ 下普通 SELECT 是快照读，
        //   读不到别的事务刚提交的记录 → 并发时会全部判"无冲突"→ 超卖。
        //   这是第 9 周并发自测实际打出来的问题（10 并发出了 10 条有效预约）。
        List<OccupiedSlotDTO> occupied = meetingRoomMapper.selectRoomOccupiedSlotsForUpdate(request.getRoomId(), reserveDate);
        List<OccupiedSlotVO> conflicts = findConflicts(occupied, start, end);
        if (!conflicts.isEmpty()) {
            // ★ 409 + 被占用时段：FR-10 要求"说明冲突原因和已被占用的时间段"
            log.info("预约冲突：roomId={}, new=[{}, {}), 冲突时段数={}",
                    request.getRoomId(), start, end, conflicts.size());
            throw new BusinessException(ApiResponse.CODE_CONFLICT,
                    "该会议室在此时间段已被占用", ReservationCreateVO.conflict(conflicts));
        }

        // ---------- 第四步：落库（FR-07）----------
        Long userId = CurrentUser.userId();
        if (userId == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录或登录已过期，请重新登录");
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setRoomId(request.getRoomId());
        reservation.setReserveDate(reserveDate);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setTopic(request.getTopic().trim());
        reservation.setAttendeeCount(request.getAttendeeCount());
        reservation.setContact(request.getContact().trim());
        reservation.setPurpose(request.getPurpose() == null || request.getPurpose().isBlank()
                ? null : request.getPurpose().trim());

        int inserted = reservationMapper.insert(reservation);
        if (inserted != 1) {
            throw new BusinessException(ApiResponse.CODE_SERVER_ERROR, "预约保存失败，请稍后重试");
        }

        // ---------- 第五步：按自增主键回填编号（并发安全）----------
        String reservationNo = buildReservationNo(reservation.getId(), reserveDate);
        reservationMapper.updateReservationNo(reservation.getId(), reservationNo);

        return ReservationCreateVO.success(reservation.getId(), reservationNo, "PENDING");
    }

    /**
     * ★ 半开区间冲突判定（FR-09）。
     *
     * <p>判定式<strong>只能</strong>写 {@code start_new < end_old AND end_new > start_old}。
     * 若写成 {@code <=}，首尾相接的两段（09:00—10:00 与 10:00—11:00）会被误判为冲突。</p>
     *
     * <p>已取消（CANCELLED）的预约不算占用——时段已释放（NF-08），
     * 这一点已在 {@code selectRoomOccupiedSlots} 的 SQL 里用 {@code status <> 'CANCELLED'} 保证。</p>
     *
     * @param occupied 当日已有的占用行
     * @param startNew  新预约的开始时间
     * @param endNew    新预约的结束时间
     * @return 冲突的时段列表；无冲突时返回空列表
     */
    private List<OccupiedSlotVO> findConflicts(List<OccupiedSlotDTO> occupied,
                                                LocalDateTime startNew,
                                                LocalDateTime endNew) {
        List<OccupiedSlotVO> conflicts = new ArrayList<>();
        for (OccupiedSlotDTO slot : occupied) {
            LocalDateTime startOld = slot.getStartTime();
            LocalDateTime endOld = slot.getEndTime();
            // ★★★ 判定式（唯一一处，勿动）★★★
            //   重叠 = start_new < end_old  且  end_new > start_old
            //   严格不等号 → 首尾相接（start_new == end_old）不算冲突
            boolean overlap = startNew.isBefore(endOld) && endNew.isAfter(startOld);
            if (overlap) {
                conflicts.add(new OccupiedSlotVO(startOld, endOld));
            }
        }
        return conflicts;
    }

    /** 必填校验（FR-07：主题、参会人数、联系人、起止时间为必填） */
    private void validateRequired(BookingRequest request) {
        if (request == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写完整的预约信息");
        }
        if (request.getRoomId() == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择会议室");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请选择预约的开始和结束时间");
        }
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写会议主题");
        }
        if (request.getAttendeeCount() == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写参会人数");
        }
        if (request.getAttendeeCount() <= 0) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "参会人数必须大于 0");
        }
        if (request.getContact() == null || request.getContact().isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写联系人");
        }
    }

    /**
     * 时间规则校验（FR-08）。
     *
     * <p>三条规则：① 结束时间必须晚于开始时间；② 不得跨天；③ 单次不超过 8 小时。
     * 另外加一条：开始时间不得早于当前时间。</p>
     */
    private void validateTimeRule(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "预约不能跨天，且单次不能超过 8 小时");
        }
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (minutes > MAX_MINUTES) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "预约不能跨天，且单次不能超过 8 小时");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "预约开始时间不能早于当前时间");
        }
    }

    /** 会议室存在性预检（FR-06 的停用判断在锁行后再做一次，见 create 流程） */
    private void validateRoomBookable(Long roomId) {
        MeetingRoom room = meetingRoomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }
    }

    /**
     * 生成预约编号：{@code MR} + {@code yyyyMMdd} + 左补零的自增主键。
     *
     * <p>形如 {@code MR20261012001}，共 13 位字符，符合 {@code VARCHAR(32)}。</p>
     *
     * <p><b>★ 为什么不用"当日 3 位流水号"：</b>并发提交时多个请求会算出同一个流水号，
     * 撞唯一键 {@code uk_reservation_no} → 大量请求报 500，第 11 周的 50 并发测试
     * 会直接判负。自增主键全局唯一且递增，天然规避这个问题。</p>
     *
     * <p><b>★ 为什么用完整主键而不是 {@code id % 1000}：</b>取模会让主键 1 与 1001
     * 在同一天生成同一个编号 → 撞唯一键，且这种撞只在数据量涨上去后才出现，
     * 极难排查。这里直接用完整主键，位数不够时左补零。</p>
     */
    private String buildReservationNo(Long id, LocalDate date) {
        return "MR" + date.format(NO_DATE_FORMAT) + String.format("%03d", id);
    }
}
