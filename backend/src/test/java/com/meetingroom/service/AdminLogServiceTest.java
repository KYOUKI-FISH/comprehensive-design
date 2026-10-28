package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.dto.AdminLogQuery;
import com.meetingroom.dto.OperationLogVO;
import com.meetingroom.dto.PageResult;
import com.meetingroom.mapper.OperationLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端操作日志查询用例（接口 #13，NF-14）。
 *
 * <p>覆盖"字段完整""只读不删""ip 固定 null 不假装有值"三点。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @InjectMocks
    private AdminLogService adminLogService;

    private OperationLogVO row(String type, String targetType, Long targetId, String remark) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(1L);
        vo.setOperatorUsername("admin");
        vo.setOperatorName("系统管理员");
        vo.setOperatorRole("ADMIN");
        vo.setOperationType(type);
        vo.setTargetType(targetType);
        vo.setTargetId(targetId);
        vo.setRemark(remark);
        return vo;
    }

    @Test
    @DisplayName("查询：返回 total／pageNum／pageSize／list 四项齐全")
    void 查询结构完整() {
        when(operationLogMapper.countList(any())).thenReturn(1L);
        when(operationLogMapper.selectList(any()))
                .thenReturn(List.of(row("ROOM_DELETE", "ROOM", 5L, "管理员删除会议室：旧会议室")));

        PageResult<OperationLogVO> result = adminLogService.list(new AdminLogQuery());

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
        assertEquals(1, result.getList().size());

        OperationLogVO vo = result.getList().get(0);
        // 接口清单 #13 要求的字段逐项在位
        assertEquals("admin", vo.getOperatorUsername());
        assertEquals("系统管理员", vo.getOperatorName());
        assertEquals("ADMIN", vo.getOperatorRole());
        assertEquals("ROOM_DELETE", vo.getOperationType());
        assertEquals("ROOM", vo.getTargetType());
        assertEquals(5L, vo.getTargetId());
    }

    @Test
    @DisplayName("ip 固定为 null（建表脚本无此列，不假装有值）")
    void ip为null() {
        when(operationLogMapper.countList(any())).thenReturn(1L);
        when(operationLogMapper.selectList(any()))
                .thenReturn(List.of(row("USER_CREATE", "USER", 3L, "管理员新建账号：user3")));

        PageResult<OperationLogVO> result = adminLogService.list(new AdminLogQuery());

        assertNull(result.getList().get(0).getIp());
    }

    @Test
    @DisplayName("有 remark 时 detail 直接用 remark（一句话说清做了什么）")
    void detail优先用remark() {
        when(operationLogMapper.countList(any())).thenReturn(1L);
        when(operationLogMapper.selectList(any()))
                .thenReturn(List.of(row("BOOKING_FORCE_CANCEL", "BOOKING", 5L, "演示数据：管理员强制取消异常预约")));

        PageResult<OperationLogVO> result = adminLogService.list(new AdminLogQuery());

        assertEquals("演示数据：管理员强制取消异常预约", result.getList().get(0).getDetail());
    }

    @Test
    @DisplayName("无 remark 时 detail 用中文类型名拼（不甩 BOOKING_FORCE_CANCEL 这种码值）")
    void detail无remark时用中文() {
        when(operationLogMapper.countList(any())).thenReturn(1L);
        when(operationLogMapper.selectList(any()))
                .thenReturn(List.of(row("BOOKING_FORCE_CANCEL", "BOOKING", 5L, null)));

        PageResult<OperationLogVO> result = adminLogService.list(new AdminLogQuery());

        String detail = result.getList().get(0).getDetail();
        assertTrue(detail.contains("强制取消预约"), "应为中文，实际：" + detail);
        assertTrue(detail.contains("预约"), "应说明对象类型");

        assertTrue(!detail.contains("BOOKING_FORCE_CANCEL"), "不应出现码值");
    }

    @Test
    @DisplayName("0 条时不查明细")
    void 空结果不查明细() {
        when(operationLogMapper.countList(any())).thenReturn(0L);

        PageResult<OperationLogVO> result = adminLogService.list(new AdminLogQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(operationLogMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("操作类型非法 → 400（只接受 9 种）")
    void 类型非法被拒() {
        AdminLogQuery query = new AdminLogQuery();
        query.setOperationType("HACK_ATTEMPT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminLogService.list(query));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("对象类型非法 → 400（只接受 USER／ROOM／BOOKING）")
    void 对象类型非法被拒() {
        AdminLogQuery query = new AdminLogQuery();
        query.setTargetType("PASSWORD");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminLogService.list(query));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("日期倒置 → 400")
    void 日期倒置被拒() {
        AdminLogQuery query = new AdminLogQuery();
        query.setStartDate(LocalDate.of(2026, 11, 10));
        query.setEndDate(LocalDate.of(2026, 11, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminLogService.list(query));

        assertEquals(ApiResponse.CODE_PARAM_ERROR, ex.getCode());
    }

    @Test
    @DisplayName("分页上限 200（防止一次拉爆库）")
    void 分页上限() {
        when(operationLogMapper.countList(any())).thenReturn(0L);
        AdminLogQuery query = new AdminLogQuery();
        query.setPageSize(99999);

        PageResult<OperationLogVO> result = adminLogService.list(query);

        assertEquals(200, result.getPageSize());
    }

    @Test
    @DisplayName("OperationLogMapper 没有删除方法（审计链不可由业务删除）")
    void 无删除方法() {
        boolean hasDelete = false;
        for (var method : OperationLogMapper.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (name.contains("delete") || name.contains("remove")) {
                hasDelete = true;
                break;
            }
        }
        assertTrue(!hasDelete, "审计日志 Mapper 不应提供删除方法");
    }
}
