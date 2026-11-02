package com.meetingroom.dto;

/**
 * 提交预约成功的返回体（{@code POST /api/bookings} 出参，第 7 周草案 #5）。
 *
 * <pre>
 * { "code": 200, "message": "ok", "data": { "id": 8, "reservationNo": "MR20261027001", "status": "PENDING" } }
 * </pre>
 */
public class BookingVO {

    /** 预约主键（前端后续修改/取消用） */
    private Long id;
    /** 预约编号，形如 MR20261027001（13 位） */
    private String reservationNo;
    /** 状态，新建恒为 PENDING */
    private String status;

    public BookingVO() {
    }

    public BookingVO(Long id, String reservationNo, String status) {
        this.id = id;
        this.reservationNo = reservationNo;
        this.status = status;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
