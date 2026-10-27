package com.meetingroom.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交预约成功后的返回体（第 8 周冻结清单第 5 条出参）。
 *
 * <p>出参：{@code id}、{@code reservationNo}、{@code status: "PENDING"}。</p>
 *
 * <p>★ 同一个类也用于 <strong>409 冲突响应</strong>：此时 {@code reservationNo} 为 null，
 * {@code status} 为 null，多出 {@code occupiedSlots} 字段——
 * 这正是 FR-10 要求的"提示会议室已被占用及对应时段"。
 * 详见 {@code BookingService} 里的冲突分支。</p>
 */
public class ReservationCreateVO {

    /** 预约主键ID */
    private Long id;
    /** 预约编号，对外展示（FR-07） */
    private String reservationNo;
    /** 预约状态；新建预约固定为 PENDING（待开始） */
    private String status;
    /**
     * 冲突时被占用的时段（仅 409 响应里有值）。
     *
     * <p>FR-10：系统要向用户说明"已被占用的时间段"，只说"冲突了"是不够的。</p>
     */
    private List<OccupiedSlotVO> occupiedSlots = new ArrayList<>();

    public static ReservationCreateVO success(Long id, String reservationNo, String status) {
        ReservationCreateVO vo = new ReservationCreateVO();
        vo.setId(id);
        vo.setReservationNo(reservationNo);
        vo.setStatus(status);
        return vo;
    }

    public static ReservationCreateVO conflict(List<OccupiedSlotVO> occupiedSlots) {
        ReservationCreateVO vo = new ReservationCreateVO();
        vo.setOccupiedSlots(occupiedSlots);
        return vo;
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

    public List<OccupiedSlotVO> getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(List<OccupiedSlotVO> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }
}
