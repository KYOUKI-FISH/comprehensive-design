package com.meetingroom.dto;

/**
 * 提交预约的请求体（{@code POST /api/bookings} 入参，第 7 周草案 #5）。
 *
 * <pre>
 * {
 *   "roomId": 1,
 *   "date": "2026-10-27",
 *   "startTime": "2026-10-27 09:00:00",
 *   "endTime": "2026-10-27 10:00:00",
 *   "topic": "需求评审",
 *   "attendeeCount": 8,
 *   "contact": "虞业宸",
 *   "purpose": "评审第 9 周接口"
 * }
 * </pre>
 *
 * <p>★ 时间为什么用 String 收：前端页面（第 9 周）可能传 {@code 2026-10-27 09:00}、
 * 也可能只传 {@code 09:00} 配合 {@code date}。这里统一用字符串接收、由
 * {@code BookingTimeRules} 解析，避免"前端传 HH:mm、后端只认完整 datetime"这类
 * 字段格式不一致导致联调失败（检查点②要暴露的正是这类问题）。</p>
 */
public class BookingRequest {

    /** 会议室ID（必填） */
    private Long roomId;
    /** 预约日期 yyyy-MM-dd（必填；只传 HH:mm 时用它拼完整时间） */
    private String date;
    /** 开始时间，支持 yyyy-MM-dd HH:mm[:ss] / yyyy-MM-ddTHH:mm[:ss] / HH:mm[:ss] */
    private String startTime;
    /** 结束时间，格式同 startTime */
    private String endTime;
    /** 会议主题（FR-07 必填） */
    private String topic;
    /** 参会人数（FR-07 必填，> 0） */
    private Integer attendeeCount;
    /** 联系人（FR-07 必填） */
    private String contact;
    /** 用途（选填） */
    private String purpose;

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getAttendeeCount() {
        return attendeeCount;
    }

    public void setAttendeeCount(Integer attendeeCount) {
        this.attendeeCount = attendeeCount;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
