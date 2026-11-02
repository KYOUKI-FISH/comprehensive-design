package com.meetingroom.dto;

/**
 * 已占用时段（409 冲突响应的载荷元素，FR-10）。
 *
 * <p>形如 {@code { "startTime": "09:00", "endTime": "10:00" }}，
 * 只给时分、日期由请求里的 {@code date} 决定，前端可直接拼成"09:00-10:00 已被占用"。</p>
 */
public class OccupiedSlotVO {

    private String startTime;
    private String endTime;

    public OccupiedSlotVO() {
    }

    public OccupiedSlotVO(String startTime, String endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
