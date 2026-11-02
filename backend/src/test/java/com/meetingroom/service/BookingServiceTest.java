package com.meetingroom.service;

import com.meetingroom.common.BusinessException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交预约服务用例（Mock 掉 Mapper，只验业务判定与落库参数）。
 *
 * <p>覆盖第 9 周自查项：#4「失败提示是人话」、#10「首尾相接不冲突」，
 * 以及 FR-06 停用会议室、FR-09 冲突、编号生成、状态固定 PENDING。</p>
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

    @Test
    @DisplayName("正常提交：落库为 PENDING，编号 13 位，日期与开始时间一致")
    void 提交成功() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(1L, DAY)).thenReturn(Collections.emptyList());
        when(reservationMapper.selectMaxReservationNo(anyString())).thenReturn(null);

        BookingVO vo = bookingService.create(request("09:00", "10:00"), 2L);

        assertNotNull(vo);
        assertEquals("PENDING", vo.getStatus());
        assertEquals(13, vo.getReservationNo().length());
        assertEquals("MR" + DAY.toString().replace("-", "") + "001", vo.getReservationNo());

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationMapper).insert(captor.capture());
        Reservation saved = captor.getValue();
        assertEquals(2L, saved.getUserId());
        assertEquals(1L, saved.getRoomId());
        assertEquals(DAY, saved.getReserveDate());
        assertEquals("PENDING", saved.getStatus());
        assertEquals("需求评审", saved.getTopic());
    }

    @Test
    @DisplayName("正例：已有 09:00-10:00 的预约时，再订 10:00-11:00 必须成功（首尾相接不冲突）")
    void 首尾相接可以预约() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(1L, DAY)).thenReturn(List.of(slot(9, 10)));
        when(reservationMapper.selectMaxReservationNo(anyString())).thenReturn("MR" + DAY.toString().replace("-", "") + "007");

        BookingVO vo = bookingService.create(request("10:00", "11:00"), 2L);

        assertEquals("PENDING", vo.getStatus());
        verify(reservationMapper).insert(any(Reservation.class));
    }

    @Test
    @DisplayName("冲突：409 且 data.occupiedSlots 带回被占用时段（FR-10）")
    void 冲突返回占用时段() {
        when(meetingRoomMapper.selectByIdForUpdate(1L)).thenReturn(room("ENABLED"));
        when(reservationMapper.selectOccupiedSlotsOfRoom(1L, DAY)).thenReturn(List.of(slot(10, 11)));

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
}
