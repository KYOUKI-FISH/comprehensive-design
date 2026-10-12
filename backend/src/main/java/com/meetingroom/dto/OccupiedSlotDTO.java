package com.meetingroom.dto;

import java.time.LocalDateTime;

/**
 * 某会议室某天的"已占用时段"原始行（只读查询结果）。
 *
 * <p>供 DFD 的 P2「查询会议室」输出数据流"会议室列表（含当日可用时段）"使用，
 * 数据来源：{@code reservation} 表（D3）。</p>
 */
public class OccupiedSlotDTO {

    private Long roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
