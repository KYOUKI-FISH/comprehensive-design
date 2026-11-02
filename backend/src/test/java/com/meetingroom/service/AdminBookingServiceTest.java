package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.AdminBookingQuery;
import com.meetingroom.dto.PageResult;
import com.meetingroom.dto.ReservationDetailDTO;
import com.meetingroom.mapper.ReservationMapper;
import com.meetingroom.service.TokenStore;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端预约服务用例（接口 #9 全部预约、#10 强制取消）。
 *
 * <p>覆盖任务书附 1 的第 8 项（管理员四步、不填原因被拒）与第 10 项
 * （操作日志四类齐全里的"强制取消"这一类）。</p>
 *
 * <p>★ 这批用例的重点是<b>拒绝路径</b>：正常路径跑通一遍就够，真正会出问题的是
 * "原因只填 3 个字也放过了""已取消的还能再取消一次""日志没写但状态改了"。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminBookingServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final String ADMIN_NAME = "admin";
    private static final Long BOOKING_ID = 5L;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private AdminBookingService adminBookingService;

    /** 模拟已登录管理员：CurrentUser 是 ThreadLocal，测试里手动设值 */
    @BeforeEach
    void login() {
        CurrentUser.set(new TokenStore.Session(ADMIN_ID, ADMIN_NAME, "ADMIN",
                LocalDateTime.now().plusMinutes(30)));
    }

    @AfterEach
    void logout() {
        // ★ 必须清：ThreadLocal 不清理会串到下一个用例
        CurrentUser.clear();
    }

    private ReservationDetailDTO booking(String status) {
        ReservationDetailDTO dto = new ReservationDetailDTO();
        dto.setId(BOOKING_ID);
        dto.setReservationNo("MR20261102005");
        dto.setStatus(status);
        dto.setUserId(2L);
        dto.setRoomId(1L);
        return dto;
    }

    // ---------------------------------------------------------------------
    // #10 强制取消 —— 拒绝路径
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("不填原因 → 400「请填写取消原因（不少于 5 个字符）」，且不碰数据库")
    void 不填原因被拒() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, null));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("取消原因"));
        // ★ 关键：校验通过前不能有任何写操作
        verify(reservationMapper, never()).updateStatusToCancelled(anyLong(), anyString(), any());
        verify(operationLogService, never()).log(anyLong(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("原因只有 4 个字符 → 400（边界：5 个字符才通过）")
    void 原因过短被拒() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, "临时有事"));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        verify(reservationMapper, never()).updateStatusToCancelled(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("原因只由空格组成 → 400（trim 后为空也算没填）")
    void 原因全是空格被拒() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, "      "));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("预约不存在 → 404")
    void 预约不存在() {
        when(reservationMapper.selectDetailById(BOOKING_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, "该会议已取消"));

        assertEquals(ApiResponse.CODE_NOT_FOUND, ex.getCode());
        assertEquals("预约不存在", ex.getMessage());
    }

    @Test
    @DisplayName("已取消的预约再取消 → 400「已取消的预约不能重复取消」")
    void 重复取消被拒() {
        when(reservationMapper.selectDetailById(BOOKING_ID)).thenReturn(booking("CANCELLED"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, "再次取消试试"));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("重复取消"));
        verify(reservationMapper, never()).updateStatusToCancelled(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("已结束的预约不能强制取消 → 400，提示用中文状态名")
    void 已结束不能取消() {
        when(reservationMapper.selectDetailById(BOOKING_ID)).thenReturn(booking("FINISHED"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, "已结束的想取消"));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        // NF-10：提示里不能甩 FINISHED 这种码值
        assertTrue(ex.getMessage().contains("已结束"));
        assertTrue(!ex.getMessage().contains("FINISHED"));
    }

    // ---------------------------------------------------------------------
    // #10 强制取消 —— 正常路径与日志
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("正常取消：状态置 CANCELLED、写原因、同事务写一条 BOOKING_FORCE_CANCEL 日志")
    void 强制取消成功并留痕() {
        when(reservationMapper.selectDetailById(BOOKING_ID))
                .thenReturn(booking("PENDING"))        // 第一次：变更前
                .thenReturn(booking("CANCELLED"));     // 第二次：变更后回带
        when(reservationMapper.updateStatusToCancelled(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(1);

        ReservationDetailDTO result = adminBookingService.forceCancel(BOOKING_ID, "  会议取消，场地空出  ");

        assertEquals("CANCELLED", result.getStatus());

        // ① 取消原因已 trim 后落库
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(reservationMapper).updateStatusToCancelled(eq(BOOKING_ID), reasonCaptor.capture(), any());
        assertEquals("会议取消，场地空出", reasonCaptor.getValue());

        // ② NF-14：日志类型、对象、操作人逐项核对
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> targetCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> beforeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> afterCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(eq(ADMIN_ID), eq(ADMIN_NAME),
                typeCaptor.capture(), targetCaptor.capture(), eq(BOOKING_ID),
                beforeCaptor.capture(), afterCaptor.capture(), anyString());

        assertEquals("BOOKING_FORCE_CANCEL", typeCaptor.getValue());
        assertEquals("BOOKING", targetCaptor.getValue());
        // 前后数据都要能看出状态变化，审计时不必再查库
        assertTrue(beforeCaptor.getValue().contains("PENDING"));
        assertTrue(afterCaptor.getValue().contains("CANCELLED"));
        assertTrue(afterCaptor.getValue().contains("会议取消，场地空出"));
    }

    @Test
    @DisplayName("原因里带双引号 → JSON 转义，不会写出坏 JSON（否则 JSON 列插入失败）")
    void 原因含引号被正确转义() {
        when(reservationMapper.selectDetailById(BOOKING_ID)).thenReturn(booking("PENDING"));
        when(reservationMapper.updateStatusToCancelled(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(1);

        adminBookingService.forceCancel(BOOKING_ID, "主讲人说\"取消\"，场地空出");

        ArgumentCaptor<String> afterCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).log(anyLong(), anyString(), anyString(), anyString(),
                anyLong(), anyString(), afterCaptor.capture(), anyString());

        String json = afterCaptor.getValue();
        assertTrue(json.contains("\\\"取消\\\""), "双引号必须转义，实际：" + json);
        // 未转义的裸引号会让 JSON 解析失败——这里断言不会出现
        assertTrue(!json.contains("说\"取消\""), "不能出现未转义的双引号");
    }

    @Test
    @DisplayName("并发下状态已被他人改走（更新影响 0 行）→ 409，且不写日志")
    void 并发状态变更返回冲突() {
        when(reservationMapper.selectDetailById(BOOKING_ID)).thenReturn(booking("PENDING"));
        when(reservationMapper.updateStatusToCancelled(eq(BOOKING_ID), anyString(), any()))
                .thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.forceCancel(BOOKING_ID, "并发抢着取消"));

        assertEquals(ApiResponse.CODE_CONFLICT, ex.getCode());
        // ★ 状态没改成，日志不能写——否则审计里会有一条"取消了"但实际没取消的记录
        verify(operationLogService, never()).log(anyLong(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------
    // #9 全部预约
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("列表：无筛选时返回全部并带总数")
    void 列表无筛选() {
        when(reservationMapper.countAdminList(any())).thenReturn(2L);
        when(reservationMapper.selectAdminList(any())).thenReturn(List.of(booking("PENDING"), booking("CANCELLED")));

        PageResult<ReservationDetailDTO> result = adminBookingService.list(new AdminBookingQuery());

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals(1, result.getPageNum());
    }

    @Test
    @DisplayName("列表：0 条时不查明细（省一次无用查询）")
    void 列表为空不查明细() {
        when(reservationMapper.countAdminList(any())).thenReturn(0L);

        PageResult<ReservationDetailDTO> result = adminBookingService.list(new AdminBookingQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(reservationMapper, never()).selectAdminList(any());
    }

    @Test
    @DisplayName("列表：状态传非法值 → 400「只能是 PENDING／ONGOING／FINISHED／CANCELLED」")
    void 列表状态非法被拒() {
        AdminBookingQuery query = new AdminBookingQuery();
        query.setStatus("DELETED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.list(query));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("列表：结束日期早于开始日期 → 400")
    void 列表日期倒置被拒() {
        AdminBookingQuery query = new AdminBookingQuery();
        query.setStartDate(java.time.LocalDate.of(2026, 11, 5));
        query.setEndDate(java.time.LocalDate.of(2026, 11, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminBookingService.list(query));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("结束日期"));
    }

    @Test
    @DisplayName("列表：pageSize 超上限被压到 200，避免一次拉爆库")
    void 列表分页上限() {
        when(reservationMapper.countAdminList(any())).thenReturn(0L);
        AdminBookingQuery query = new AdminBookingQuery();
        query.setPageSize(9999);

        PageResult<ReservationDetailDTO> result = adminBookingService.list(query);

        assertEquals(200, result.getPageSize());
    }
}
