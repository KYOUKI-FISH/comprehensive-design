package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.RoomVO;
import com.meetingroom.entity.MeetingRoom;
import com.meetingroom.mapper.MeetingRoomMapper;
import com.meetingroom.mapper.ReservationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端会议室服务用例（接口 #11，FR-03／FR-04）。
 *
 * <p>覆盖任务书附 1 第 12 项（删会议室被拒 → 409）与第 10 项里的
 * "新增／删除会议室留痕"一类。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminRoomServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ADMIN_NAME = "admin";
    private static final Long ROOM_ID = 1L;

    @Mock
    private MeetingRoomMapper meetingRoomMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private AdminRoomService adminRoomService;

    @BeforeEach
    void login() {
        CurrentUser.set(new TokenStore.Session(ADMIN_ID, ADMIN_NAME, "ADMIN",
                LocalDateTime.now().plusMinutes(30)));
    }

    @AfterEach
    void logout() {
        CurrentUser.clear();
    }

    private MeetingRoom room(String name, String status) {
        MeetingRoom room = new MeetingRoom();
        room.setId(ROOM_ID);
        room.setName(name);
        room.setLocation("逸夫楼A座1层");
        room.setCapacity(20);
        room.setEquipment("投影仪");
        room.setStatus(status);
        return room;
    }

    private Map<String, Object> createBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "逸夫楼A303会议室");
        body.put("location", "逸夫楼A座3层");
        body.put("capacity", 16);
        body.put("equipment", "投影仪、白板");
        return body;
    }

    // ---------------------------------------------------------------------
    // #11 DELETE —— 有预约必须拒绝（FR-03，任务书卡点 8）
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("删除有预约的会议室 → 409「该会议室存在有效预约，请先处理预约」，且不执行删除")
    void 有预约不能删() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("逸夫楼A101会议室", "ENABLED"));
        when(reservationMapper.countByRoomId(ROOM_ID)).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.delete(ROOM_ID));

        assertEquals(ApiResponse.CODE_CONFLICT, ex.getCode());
        assertTrue(ex.getMessage().contains("存在有效预约"));
        assertTrue(ex.getMessage().contains("3"), "提示里应说明挂了几条，实际：" + ex.getMessage());
        // ★ 关键是这一条：拒绝时绝不能真的删
        verify(meetingRoomMapper, never()).deleteById(anyLong());
        verify(operationLogService, never()).log(anyLong(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("删除没有预约的会议室 → 成功并写 ROOM_DELETE 日志")
    void 无预约可删并留痕() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("空会议室", "ENABLED"));
        when(reservationMapper.countByRoomId(ROOM_ID)).thenReturn(0L);

        adminRoomService.delete(ROOM_ID);

        verify(meetingRoomMapper).deleteById(ROOM_ID);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), typeCaptor.capture(),
                anyString(), anyLong(), anyString(), any(), anyString());
        assertEquals("ROOM_DELETE", typeCaptor.getValue());
    }

    @Test
    @DisplayName("删除不存在的会议室 → 404")
    void 删除不存在() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.delete(ROOM_ID));

        assertEquals(ApiResponse.CODE_NOT_FOUND, ex.getCode());
        verify(meetingRoomMapper, never()).deleteById(anyLong());
    }

    // ---------------------------------------------------------------------
    // #11 POST —— 新增
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("新增会议室：落库 status 默认 ENABLED，equipment 不为 null，写 ROOM_CREATE 日志")
    void 新增成功并留痕() {
        when(meetingRoomMapper.selectByName("逸夫楼A303会议室")).thenReturn(null);
        when(meetingRoomMapper.selectById(any())).thenReturn(room("逸夫楼A303会议室", "ENABLED"));

        RoomVO vo = adminRoomService.create(createBody());

        assertNotNull(vo);
        ArgumentCaptor<MeetingRoom> captor = ArgumentCaptor.forClass(MeetingRoom.class);
        verify(meetingRoomMapper).insert(captor.capture());
        MeetingRoom saved = captor.getValue();
        assertEquals("ENABLED", saved.getStatus());
        // ★ equipment 列 NOT NULL：没填设备时必须给空串，不能给 null
        assertNotNull(saved.getEquipment());
        assertEquals(16, saved.getCapacity());

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), typeCaptor.capture(),
                anyString(), any(), any(), anyString(), anyString());
        assertEquals("ROOM_CREATE", typeCaptor.getValue());
    }

    @Test
    @DisplayName("新增重名会议室 → 400「会议室名称已存在」，且不写库")
    void 新增重名被拒() {
        when(meetingRoomMapper.selectByName("逸夫楼A303会议室"))
                .thenReturn(room("逸夫楼A303会议室", "ENABLED"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.create(createBody()));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertEquals("会议室名称已存在", ex.getMessage());
        verify(meetingRoomMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增时容量为 0 → 400「可容纳人数必须大于 0」（ck_room_capacity 兜底前先给中文提示）")
    void 容量非法被拒() {
        Map<String, Object> body = createBody();
        body.put("capacity", 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.create(body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("大于 0"));
        verify(meetingRoomMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增时缺名称 → 400「请填写会议室名称」")
    void 缺名称被拒() {
        Map<String, Object> body = createBody();
        body.remove("name");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.create(body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("会议室名称"));
    }

    // ---------------------------------------------------------------------
    // #11 PUT —— 修改／启停
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("只改 status → 日志类型是 ROOM_STATUS（不是 ROOM_UPDATE，便于按类型筛'停用'）")
    void 只改状态记ROOM_STATUS() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("逸夫楼A101", "ENABLED"));
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("逸夫楼A101", "DISABLED"));

        Map<String, Object> body = new HashMap<>();
        body.put("status", "DISABLED");
        adminRoomService.update(ROOM_ID, body);

        ArgumentCaptor<MeetingRoom> patchCaptor = ArgumentCaptor.forClass(MeetingRoom.class);
        verify(meetingRoomMapper).updateSelective(patchCaptor.capture());
        assertEquals("DISABLED", patchCaptor.getValue().getStatus());
        // 只改状态时其他字段必须留 null，否则 updateSelective 会把它们一起改掉
        assertEquals(null, patchCaptor.getValue().getName());

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), typeCaptor.capture(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
        assertEquals("ROOM_STATUS", typeCaptor.getValue());
    }

    @Test
    @DisplayName("改名称 → 日志类型是 ROOM_UPDATE")
    void 改名称记ROOM_UPDATE() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("旧名字", "ENABLED"));
        when(meetingRoomMapper.selectByName("新名字")).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "新名字");
        adminRoomService.update(ROOM_ID, body);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), typeCaptor.capture(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
        assertEquals("ROOM_UPDATE", typeCaptor.getValue());
    }

    @Test
    @DisplayName("改成别人的名字 → 400「会议室名称已存在」")
    void 改名撞别人被拒() {
        MeetingRoom other = room("已被占用", "ENABLED");
        other.setId(99L);
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("我的会议室", "ENABLED"));
        when(meetingRoomMapper.selectByName("已被占用")).thenReturn(other);

        Map<String, Object> body = new HashMap<>();
        body.put("name", "已被占用");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.update(ROOM_ID, body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        verify(meetingRoomMapper, never()).updateSelective(any());
    }

    @Test
    @DisplayName("改成自己原来的名字 → 放行（同名不触发查重，因为和原名相同跳过比对）")
    void 改成自己的名字可放行() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("原名字", "ENABLED"));

        Map<String, Object> body = new HashMap<>();
        body.put("name", "原名字");
        // 不应抛异常
        adminRoomService.update(ROOM_ID, body);

        verify(meetingRoomMapper).updateSelective(any());
        // ★ 名字没变，连查重都不必走——省一次库查询，也避免"自己和自己重名"的误判
        verify(meetingRoomMapper, never()).selectByName(anyString());
    }

    @Test
    @DisplayName("空 body → 400，不写库")
    void 空更新被拒() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("逸夫楼A101", "ENABLED"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.update(ROOM_ID, new HashMap<>()));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        verify(meetingRoomMapper, never()).updateSelective(any());
    }

    @Test
    @DisplayName("状态传非法值 → 400「只能是 ENABLED 或 DISABLED」")
    void 状态非法被拒() {
        when(meetingRoomMapper.selectById(ROOM_ID)).thenReturn(room("逸夫楼A101", "ENABLED"));

        Map<String, Object> body = new HashMap<>();
        body.put("status", "DELETED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminRoomService.update(ROOM_ID, body));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("管理端列表不过滤停用（管理员要能看到并重新启用）")
    void 管理端列表含停用() {
        when(meetingRoomMapper.selectByCondition(null, null, null))
                .thenReturn(List.of(room("A", "ENABLED"), room("B", "DISABLED")));

        List<RoomVO> rooms = adminRoomService.list(null, null, null);

        assertEquals(2, rooms.size());
        // status 为空表示"不筛"，Service 不能自己偷偷加上 ENABLED
        verify(meetingRoomMapper).selectByCondition(null, null, null);
    }
}
