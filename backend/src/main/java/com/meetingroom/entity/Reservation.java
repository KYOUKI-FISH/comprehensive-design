package com.meetingroom.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约记录实体 —— 对应建表脚本 {@code reservation} 表（DFD 数据存储 D3 预约记录）。
 *
 * <p>字段与 {@code docs/week07-08/schema.sql} 逐列对应，<b>不要凭记忆加字段</b>：
 * 第 8 周定稿的该表没有 {@code operator_id}（管理员处理留痕走 {@code operation_log}），
 * 加了会与建表脚本不一致，插入直接报 Unknown column。</p>
 */
public class Reservation {

    /** 主键，对外不暴露（对外用 reservationNo） */
    private Long id;
    /** 预约编号：MR + yyyyMMdd + 自增主键左补零至 3 位，插入后回填，UNIQUE（第 10 周改造） */
    private String reservationNo;
    /** 预约人 → user.id */
    private Long userId;
    /** 会议室 → meeting_room.id */
    private Long roomId;
    /** 预约日期，恒等于 DATE(startTime)（由 CHECK ck_reservation_date_match 保证） */
    private LocalDate reserveDate;
    /** 开始时间（半开区间左闭，含本时刻） */
    private LocalDateTime startTime;
    /** 结束时间（半开区间右开，不含本时刻） */
    private LocalDateTime endTime;
    /** 会议主题（FR-07 必填） */
    private String topic;
    /** 参会人数（FR-07 必填，> 0） */
    private Integer attendeeCount;
    /** 联系人（FR-07 必填） */
    private String contact;
    /** 用途补充说明（选填） */
    private String purpose;
    /** 状态四态：PENDING / ONGOING / FINISHED / CANCELLED（NF-08） */
    private String status;
    /** 取消原因（FR-16 强制取消必填） */
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
