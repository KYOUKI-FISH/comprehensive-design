package com.meetingroom.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约实体 —— 对应建表脚本 {@code reservation} 表（DFD 数据存储 D3 预约记录）。
 *
 * <p>★ 时间字段的语义（半开区间 {@code [start_time, end_time)}）：
 * 开始时间<strong>含</strong>本时刻，结束时间<strong>不含</strong>本时刻。
 * 因此"09:00—10:00"与"10:00—11:00"两段首尾相接，<strong>不算冲突</strong>（FR-09）。</p>
 */
public class Reservation {

    /** 预约主键ID，自增；对外不暴露，对外用 reservationNo */
    private Long id;
    /** 预约编号，全局唯一，对外展示（FR-07） */
    private String reservationNo;
    /** 预约人ID → user(id) */
    private Long userId;
    /** 会议室ID → meeting_room(id) */
    private Long roomId;
    /** 预约日期，恒等于 DATE(start_time)，由 CHECK 保证 */
    private LocalDate reserveDate;
    /** 开始时间，含本时刻 */
    private LocalDateTime startTime;
    /** 结束时间，不含本时刻 */
    private LocalDateTime endTime;
    /** 会议主题，必填（FR-07） */
    private String topic;
    /** 参会人数，必填且大于 0（FR-07） */
    private Integer attendeeCount;
    /** 联系人，必填（FR-07） */
    private String contact;
    /** 用途补充说明，选填 */
    private String purpose;
    /** 预约状态：PENDING / ONGOING / FINISHED / CANCELLED（NF-08 四态） */
    private String status;
    /** 取消原因；管理员强制取消时必填（FR-16） */
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public void setReservationNo(String reservationNo) {
        this.reservationNo = reservationNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public LocalDate getReserveDate() {
        return reserveDate;
    }

    public void setReserveDate(LocalDate reserveDate) {
        this.reserveDate = reserveDate;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
