package com.meetingroom.dto;

import java.time.LocalDateTime;

/**
 * 占用时段元素（GET /api/rooms/{id}/occupancy 出参里的 {@code occupiedSlots} 数组元素）。
 *
 * <p><b>为什么出参是"起止时间对象"而不是字符串：</b>
 * 第 8 周冻结的接口清单第 4 条规定出参为
 * {@code occupiedSlots: {startTime, endTime}[]}。
 * 若前端只拿到 {@code "09:00-10:00"} 这样的字符串，就无法判断"这条占用与我选的时段是否重叠"，
 * 只能靠人眼比对；给起止时间对象，前端才能直接算出重叠、也能在表单里标出冲突区间。
 *
 * <p>注意：这里给的是<strong>时间点</strong>，不是区间文本。真正的冲突判定
 * （半开区间 {@code start_new < end_old AND end_new > start_old}）在服务端做（FR-09），
 * 前端只做展示。
 */
public class OccupiedSlotVO {

    /** 占用开始时间（含本时刻，半开区间左闭） */
    private LocalDateTime startTime;
    /** 占用结束时间（不含本时刻，半开区间右开） */
    private LocalDateTime endTime;

    public OccupiedSlotVO() {
    }

    public OccupiedSlotVO(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
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
