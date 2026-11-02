package com.meetingroom.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端预约视图（接口 #9 出参、#10 出参）。
 *
 * <p>在 {@code reservation} 实体字段之外多带三个联表／派生字段：</p>
 * <ul>
 *   <li>{@code userName} —— 预约人的姓名，管理员要看出"是谁订的"；</li>
 *   <li>{@code roomName} —— 会议室名称，比裸 {@code roomId} 可读；</li>
 *   <li>{@code operatorName} —— 最后一次强制取消它的管理员，来自 {@code operation_log}
 *       的最新一条 {@code BOOKING_FORCE_CANCEL}。★ 需求清单 §接口 #9 明确要求带这一项。</li>
 * </ul>
 *
 * <p><b>★ 为什么用查询类而不是在实体上加 {@code @Transient} 字段：</b>
 * 实体与建表脚本逐列对应（第 8 周定稿口径），加非列字段会让"实体即表"这个
 * 约定失效，后续有人照着实体去写 INSERT 会直接报 Unknown column。</p>
 */
public class ReservationDetailDTO {

    private Long id;
    private String reservationNo;
    private Long userId;
    /** 预约人姓名（联表 user.real_name） */
    private String userName;
    private Long roomId;
    /** 会议室名称（联表 meeting_room.name） */
    private String roomName;
    private LocalDate reserveDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String topic;
    private Integer attendeeCount;
    private String contact;
    private String purpose;
    private String status;
    private String cancelReason;
    /** 最后强制取消该预约的管理员账号（来自 operation_log，可为 null） */
    private String operatorName;
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

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

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
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
