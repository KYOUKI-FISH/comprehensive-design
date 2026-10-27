package com.meetingroom.dto;

import com.meetingroom.entity.MeetingRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * 会议室详情（GET /api/rooms/{id} 的返回体）。
 *
 * <p><b>为什么详情页不另开一个"取单个会议室"的接口：</b>
 * 任务书第 9 周表 1 出现过 {@code GET /api/rooms/{id}}，但第 7—8 周冻结的 14 条接口清单里
 * 没有这一条——清单里与"详情"对应的是第 4 条 {@code GET /api/rooms/{id}/occupancy}
 * （会议室占用时段）。而 {@code GET /api/rooms} 返回的 {@link RoomVO} 已经带全了
 * 详情页要展示的字段（名称／位置／容量／设备／状态）。
 *
 * <p>因此本组的做法是<strong>组合供数</strong>：详情页的<strong>基本信息</strong>来自
 * {@code GET /api/rooms} 列表项，<strong>当日占用时段</strong>来自
 * {@code GET /api/rooms/{id}/occupancy}。这样接口总数仍为 14 条，
 * 与第 7—8 周设计保持同源，不为验收新增第 15 条接口。
 * 详见 {@code docs/week09/01_接口变更记录.md} 决策 D1。</p>
 */
public class RoomDetailVO {

    private Long id;
    private String name;
    private String location;
    private Integer capacity;
    private String equipment;
    /** ENABLED=启用 / DISABLED=停用（FR-06：停用会议室不可被预约） */
    private String status;
    private String remark;
    /** 所选日期的已占用时段，形如 ["09:00-10:00"]；当日无占用时为空数组 */
    private List<String> occupiedSlots = new ArrayList<>();

    public static RoomDetailVO from(MeetingRoom room) {
        RoomDetailVO vo = new RoomDetailVO();
        vo.setId(room.getId());
        vo.setName(room.getName());
        vo.setLocation(room.getLocation());
        vo.setCapacity(room.getCapacity());
        vo.setEquipment(room.getEquipment());
        vo.setStatus(room.getStatus());
        vo.setRemark(room.getRemark());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<String> getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(List<String> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }
}
