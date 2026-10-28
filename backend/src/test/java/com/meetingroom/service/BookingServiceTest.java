package com.meetingroom.service;

import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.BookingItemVO;
import com.meetingroom.dto.BookingRequest;
import com.meetingroom.dto.BookingVO;
import com.meetingroom.dto.ConflictPayload;
import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.entity.MeetingRoom;
import com.meetingroom.entity.Reservation;
import com.meetingroom.mapper.MeetingRoomMapper;
import com.meetingroom.mapper.ReservationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约闭环服务用例（Mock 掉 Mapper，只验业务判定与落库参数）。
 *
 * <p>第 9 周覆盖：提交冲突／停用室／首尾相接正例／409 载荷；
 * 第 10 周新增：我的预约（四态推导）、修改（本人／30 分钟／排除自身的冲突重检）、
 * 取消（30 分钟／CANCELLED）、编号"占位插入 → 主键派生回填"。</p>
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    /** 用未来日期，避免撞上"开始时间不得早于当前时间" */
    private static final LocalDate DAY = LocalDate.now().plusDays(3);
    private static final String DATE_TEXT = DAY.toString();

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private MeetingRoomMapper meetingRoomMapper;

    @InjectMocks
    private BookingService bookingService;

    private BookingRequest request(String start, String end) {
        BookingRequest request = new BookingRequest();
        request.setRoomId(1L);
        request.setDate(DATE_TEXT);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setTopic("需求评审");
        request.setAttendeeCount(8);
        request.setContact("虞业宸");
        return request;
    }

    private MeetingRoom room(String status) {
        MeetingRoom room = new MeetingRoom();
        room.setId(1L);
        room.setName("逸夫楼 A101");
        room.setStatus(status);
        return room;
    }

    private OccupiedSlotDTO slot(int startHour, int endHour) {
        OccupiedSlotDTO dto = new OccupiedSlotDTO();
        dto.setRoomId(1L);
        dto.setStartTime(LocalDateTime.of(DAY, LocalTime.of(startHour, 0)));
        dto.setEndTime(LocalDateTime.of(DAY, LocalTime.of(endHour, 0)));
        return dto;
    }

    /** 让 Mock 的 insert 像真 MyBatis 一样回填自增主键 */
    private void insertBackfillsId(long id) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Reservation.class).setId(id);
            return 1;
        }).when(reservationMapper).insert(any(Reservation.class));
    }

    /** 断言提交走了"占位插入 → 派生编号回填"两步 */
    private void assertNoBackfilled(long id, String expectedNo) {
        ArgumentCaptor<String> noCaptor = ArgumentCaptor.forClass(String.class);
        verify(reservationMapper).updateReservationNo(eq(id), noCaptor.capture());
        assertEquals(expectedNo, noCaptor.getValue());
    }

    // ==================== 提交（#5，第 10 周编号方案改造） ====================

    @Test
    @DisplayName("正常提交：落库 PENDING，编号由主键派生（MR+日期+补零主键）")
    void 提交成功() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(eq(1L), eq(DAY), eq(null)))
                .thenReturn(Collections.emptyList());
        insertBackfillsId(8L);

        BookingVO vo = bookingService.create(request("09:00", "10:00"), 2L);

        assertNotNull(vo);
        assertEquals("PENDING", vo.getStatus());
        assertEquals(8L, vo.getId());
        assertEquals("MR" + DATE_TEXT.replace("-", "") + "008", vo.getReservationNo());

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationMapper).insert(captor.capture());
        Reservation saved = captor.getValue();
        assertEquals(2L, saved.getUserId());
        assertEquals(1L, saved.getRoomId());
        assertEquals(DAY, saved.getReserveDate());
        assertEquals("PENDING", saved.getStatus());
        assertEquals("需求评审", saved.getTopic());
        assertNoBackfilled(8L, "MR" + DATE_TEXT.replace("-", "") + "008");
    }

    @Test
    @DisplayName("正例：已有 09:00-10:00 的预约时，再订 10:00-11:00 必须成功（首尾相接不冲突）")
    void 首尾相接可以预约() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(eq(1L), eq(DAY), eq(null)))
                .thenReturn(List.of(slot(9, 10)));
        insertBackfillsId(9L);

        BookingVO vo = bookingService.create(request("10:00", "11:00"), 2L);

        assertEquals("PENDING", vo.getStatus());
        verify(reservationMapper).insert(any(Reservation.class));
    }

    @Test
    @DisplayName("冲突：409 且 data.occupiedSlots 带回被占用时段（FR-10）")
    void 冲突返回占用时段() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(eq(1L), eq(DAY), eq(null)))
                .thenReturn(List.of(slot(10, 11)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request("09:30", "10:30"), 2L));

        assertEquals(409, e.getCode());
        assertEquals("该会议室在此时间段已被占用", e.getMessage());
        ConflictPayload payload = (ConflictPayload) e.getData();
        assertNotNull(payload);
        assertEquals(1, payload.getOccupiedSlots().size());
        assertEquals("10:00", payload.getOccupiedSlots().get(0).getStartTime());
        assertEquals("11:00", payload.getOccupiedSlots().get(0).getEndTime());
        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    @Test
    @DisplayName("停用的会议室不能预约（FR-06），提示是人话")
    void 停用会议室() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("DISABLED"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request("09:00", "10:00"), 2L));

        assertEquals(400, e.getCode());
        assertEquals("该会议室已停用，无法预约", e.getMessage());
        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    @Test
    @DisplayName("未登录：401")
    void 未登录() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request("09:00", "10:00"), null));
        assertEquals(401, e.getCode());
        assertEquals("未登录", e.getMessage());
    }

    @Test
    @DisplayName("会议室不存在：404")
    void 会议室不存在() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request("09:00", "10:00"), 2L));

        assertEquals(404, e.getCode());
        assertEquals("会议室不存在", e.getMessage());
    }

    @Test
    @DisplayName("缺必填：会议主题为空时 400，且提示不是字段名")
    void 缺会议主题() {
        BookingRequest request = request("09:00", "10:00");
        request.setTopic(" ");

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request, 2L));

        assertEquals(400, e.getCode());
        assertEquals("请填写会议主题", e.getMessage());
    }

    @Test
    @DisplayName("参会人数为 0：400（FR-07）")
    void 人数为零() {
        BookingRequest request = request("09:00", "10:00");
        request.setAttendeeCount(0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request, 2L));

        assertEquals(400, e.getCode());
        assertEquals("参会人数必须是大于 0 的整数", e.getMessage());
    }

    @Test
    @DisplayName("结束时间早于开始时间：400（FR-08），此时不该去锁会议室")
    void 时间倒挂() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.create(request("11:00", "10:00"), 2L));

        assertEquals(400, e.getCode());
        assertEquals("结束时间必须晚于开始时间", e.getMessage());
        verify(meetingRoomMapper, never()).selectByIdForUpdate(any());
    }

    // ==================== 我的预约（#6） ====================

    @Test
    @DisplayName("我的预约：先落库校正过期 PENDING，再查询本人列表并推导状态（FR-11）")
    void 我的预约列表() {
        BookingItemVO item = new BookingItemVO();
        item.setId(8L);
        item.setStartTime(LocalDateTime.now().plusHours(2));
        item.setEndTime(LocalDateTime.now().plusHours(3));
        when(reservationMapper.selectMine(2L)).thenReturn(List.of(item));

        List<BookingItemVO> result = bookingService.mine(2L);

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
        verify(reservationMapper).correctOverduePending();
    }

    @Test
    @DisplayName("我的预约未登录：401，且不触库")
    void 我的预约未登录() {
        BusinessException e = assertThrows(BusinessException.class, () -> bookingService.mine(null));
        assertEquals(401, e.getCode());
        verify(reservationMapper, never()).selectMine(any());
    }

    // ==================== 修改（#7） ====================

    @Test
    @DisplayName("修改成功：排除自身重检冲突，业务字段全部更新（FR-12）")
    void 修改成功() {
        Reservation existing = reservation(2L, "PENDING", 8, 9);
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(existing);
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        // 同日已有别人 09:00-10:00；我改的就是 8-9 → 不与新时段冲突
        when(reservationMapper.selectOccupiedSlotsOfRoom(eq(1L), eq(DAY), eq(5L)))
                .thenReturn(List.of(slot(9, 10)));

        BookingVO vo = bookingService.modify(5L, request("08:00", "09:00"), 2L);

        assertEquals(5L, vo.getId());
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationMapper).updateBookingFields(captor.capture());
        assertEquals(LocalDateTime.of(DAY, LocalTime.of(8, 0)), captor.getValue().getStartTime());
        assertEquals(LocalDateTime.of(DAY, LocalTime.of(9, 0)), captor.getValue().getEndTime());
    }

    @Test
    @DisplayName("修改后的时段与他人冲突：409，不落库")
    void 修改后冲突() {
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(reservation(2L, "PENDING", 8, 9));
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(eq(1L), eq(DAY), eq(5L)))
                .thenReturn(List.of(slot(9, 10)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.modify(5L, request("09:30", "10:30"), 2L));

        assertEquals(409, e.getCode());
        verify(reservationMapper, never()).updateBookingFields(any(Reservation.class));
    }

    @Test
    @DisplayName("改别人的预约：403（数据边界）")
    void 修改他人的() {
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(reservation(3L, "PENDING", 8, 9));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.modify(5L, request("08:00", "09:00"), 2L));

        assertEquals(403, e.getCode());
        assertEquals("只能修改本人提交的预约", e.getMessage());
    }

    @Test
    @DisplayName("修改不存在的预约：404")
    void 修改不存在() {
        when(reservationMapper.selectByIdForUpdate(99L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.modify(99L, request("08:00", "09:00"), 2L));

        assertEquals(404, e.getCode());
    }

    @Test
    @DisplayName("已取消的预约不能修改（NF-08 状态准入）")
    void 修改已取消() {
        when(reservationMapper.selectByIdForUpdate(5L))
                .thenReturn(reservation(2L, "CANCELLED", 8, 9));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.modify(5L, request("08:00", "09:00"), 2L));

        assertEquals(400, e.getCode());
        assertEquals("该预约已取消，无需再修改", e.getMessage());
    }

    // ==================== 取消（#8） ====================

    @Test
    @DisplayName("取消成功：状态 CANCELLED，原因可为空（FR-13）")
    void 取消成功() {
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(reservation(2L, "PENDING", 8, 9));

        BookingVO vo = bookingService.cancel(5L, "时间冲突了", 2L);

        assertEquals("CANCELLED", vo.getStatus());
        verify(reservationMapper).updateStatus(5L, "CANCELLED", "时间冲突了");
    }

    @Test
    @DisplayName("取消原因空白 → 存 null，不存空串")
    void 取消原因为空() {
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(reservation(2L, "PENDING", 8, 9));

        bookingService.cancel(5L, "  ", 2L);

        verify(reservationMapper).updateStatus(5L, "CANCELLED", null);
    }

    @Test
    @DisplayName("取消别人的预约：403")
    void 取消他人的() {
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(reservation(3L, "PENDING", 8, 9));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.cancel(5L, null, 2L));

        assertEquals(403, e.getCode());
        assertEquals("只能取消本人提交的预约", e.getMessage());
    }

    @Test
    @DisplayName("已取消的预约不能重复取消（NF-08）")
    void 重复取消() {
        when(reservationMapper.selectByIdForUpdate(5L))
                .thenReturn(reservation(2L, "CANCELLED", 8, 9));

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.cancel(5L, null, 2L));

        assertEquals(400, e.getCode());
        assertEquals("该预约已取消，无需再取消", e.getMessage());
        verify(reservationMapper, never()).updateStatus(any(), anyString(), any());
    }

    @Test
    @DisplayName("30 分钟口径：距开始不足 30 分钟的 PENDING 预约不能取消（FR-13）")
    void 不足三十分钟不可取消() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(20);
        Reservation soon = new Reservation();
        soon.setId(5L);
        soon.setUserId(2L);
        soon.setRoomId(1L);
        soon.setStatus("PENDING");
        soon.setStartTime(start);
        soon.setEndTime(start.plusHours(1));
        when(reservationMapper.selectByIdForUpdate(5L)).thenReturn(soon);

        BusinessException e = assertThrows(BusinessException.class,
                () -> bookingService.cancel(5L, null, 2L));

        assertEquals(400, e.getCode());
        assertEquals("距会议开始不足 30 分钟，不能取消", e.getMessage());
        verify(reservationMapper, never()).updateStatus(any(), anyString(), any());
    }

    // ==================== 测试脚手架 ====================

    /** 构造一条"明天 8 点开始"的库内预约（距现在远超 30 分钟，准入判定能通过） */
    private Reservation reservation(long ownerId, String status, int startHour, int endHour) {
        Reservation reservation = new Reservation();
        reservation.setId(5L);
        reservation.setUserId(ownerId);
        reservation.setRoomId(1L);
        reservation.setReserveDate(DAY);
        reservation.setStatus(status);
        reservation.setStartTime(LocalDateTime.of(DAY, LocalTime.of(startHour, 0)));
        reservation.setEndTime(LocalDateTime.of(DAY, LocalTime.of(endHour, 0)));
        reservation.setReservationNo("MR" + DATE_TEXT.replace("-", "") + "005");
        return reservation;
    }
}
