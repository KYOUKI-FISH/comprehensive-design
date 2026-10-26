package com.meetingroom.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提交预约请求体（POST /api/bookings 的入参）。
 *
 * <p>对应第 8 周冻结接口清单第 5 条：{@code roomId}、{@code date}、{@code startTime}、
 * {@code endTime}、{@code topic}（必填）、{@code attendeeCount}（必填）、
 * {@code contact}（必填）、{@code purpose}（选填）。</p>
 *
 * <p>★ 必填校验在 Service 层做，<strong>不靠 {@code @NotNull} 注解</strong>——
 * 本项目没有引 validation starter，且 FR-07 要求给的是<strong>面向使用者的中文提示</strong>
 * （"请填写会议主题"而不是"topic must not be null"），手写校验更可控。</p>
 */
public class BookingRequest {

    /** 会议室ID */
    private Long roomId;
    /** 预约日期 yyyy-MM-dd */
    private LocalDate date;
    /** 开始时间 yyyy-MM-dd HH:mm（也可传 yyyy-MM-ddTHH:mm） */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 会议主题，必填（FR-07） */
    private String topic;
    /** 参会人数，必填且大于 0（FR-07） */
    private Integer attendeeCount;
    /** 联系人，必填（FR-07） */
    private String contact;
    /** 用途补充说明，选填 */
    private String purpose;

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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
