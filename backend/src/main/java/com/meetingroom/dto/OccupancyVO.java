package com.meetingroom.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 会议室占用时段响应体（GET /api/rooms/{id}/occupancy 的返回体）。
 *
 * <p>对应第 8 周冻结接口清单第 4 条：出参含 {@code roomId}、{@code roomName}、
 * {@code date}、{@code occupiedSlots}。</p>
 *
 * <p><b>★ 只含 {@code status <> 'CANCELLED'} 的记录</b>——已取消的预约已经把时段释放了，
 * 继续算作占用会让用户以为时段被占（NF-08"取消后时段立即释放"）。
 */
public class OccupancyVO {

    private Long roomId;
    private String roomName;
    /** 所查询的日期 */
    private LocalDate date;
    /** 已占用时段；当日无占用时为空数组 */
    private List<OccupiedSlotVO> occupiedSlots = new ArrayList<>();

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<OccupiedSlotVO> getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(List<OccupiedSlotVO> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }
}
