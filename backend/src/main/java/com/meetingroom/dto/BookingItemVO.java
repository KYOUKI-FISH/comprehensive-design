package com.meetingroom.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "我的预约"列表项（{@code GET /api/bookings/mine} 出参，第 10 周接口 #6）。
 *
 * <pre>
 * { "id":8, "reservationNo":"MR20261028008", "roomId":1, "roomName":"逸夫楼 A101",
 *   "location":"逸夫楼一层", "date":"2026-10-28", "startTime":"2026-10-28 09:00:00",
 *   "endTime":"2026-10-28 10:00:00", "topic":"需求评审", "attendeeCount":8,
 *   "contact":"虞业宸", "purpose":null, "status":"PENDING", "cancelReason":null }
 * </pre>
 *
 * <p>★ {@code status} 是<b>读取时推导后的四态</b>（{@code ReservationStatusPolicy.derive}），
 * 不是库里的原始存储值——过去但没校正的 PENDING 在这里看到的就是 FINISHED（NF-08）。</p>
 */
public class BookingItemVO {

    /** 预约主键（前端修改/取消用它拼路径） */
    private Long id;
    /** 预约编号 */
    private String reservationNo;
    /** 会议室ID */
    private Long roomId;
    /** 会议室名称 */
    private String roomName;
    /** 会议室位置 */
    private String location;
    /** 预约日期 yyyy-MM-dd */
    private LocalDate date;
    /** 开始时间 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime startTime;
    /** 结束时间 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime endTime;
    /** 会议主题 */
    private String topic;
    /** 参会人数 */
    private Integer attendeeCount;
    /** 联系人 */
    private String contact;
    /** 用途说明（可空） */
    private String purpose;
    /** 展示状态（推导后的四态） */
    private String status;
    /** 取消原因（用户取消可空；被管理员强制取消时有值） */
    private String cancelReason;

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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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
}
