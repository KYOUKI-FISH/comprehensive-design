package com.meetingroom.dto;

import java.util.List;

/**
 * 409 冲突响应的数据载荷（FR-10）。
 *
 * <pre>
 * {
 *   "code": 409,
 *   "message": "该会议室在此时间段已被占用",
 *   "data": { "occupiedSlots": [ { "startTime": "10:00", "endTime": "11:00" } ] }
 * }
 * </pre>
 *
 * <p>前端拿到它就能把"被占用的时段"直接显示在预约表单上，而不是只弹一句"失败"。</p>
 */
public class ConflictPayload {

    private List<OccupiedSlotVO> occupiedSlots;

    public ConflictPayload() {
    }

    public ConflictPayload(List<OccupiedSlotVO> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }

    public List<OccupiedSlotVO> getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(List<OccupiedSlotVO> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }
}
