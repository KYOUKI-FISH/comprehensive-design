package com.meetingroom.dto;

import com.meetingroom.entity.MeetingRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * 会议室列表项（GET /api/rooms 的返回元素）。
 *
 * <p>在实体字段之外多带一个 {@code occupiedSlots}：指定 {@code date} 参数时，
 * 给出该会议室当天的已占用时段（形如 {@code "09:00-10:00"}），供查询页展示。</p>
 */
public class RoomVO {

    private Long id;
    private String name;
    private String location;
    private Integer capacity;
    private String equipment;
    /** 启用状态：ENABLED / DISABLED（FR-06 显示会议室可用状态） */
    private String status;
    /** 指定日期时该会议室的已占用时段；未指定日期时为空数组 */
    private List<String> occupiedSlots = new ArrayList<>();

    public static RoomVO from(MeetingRoom room) {
        RoomVO vo = new RoomVO();
        vo.setId(room.getId());
        vo.setName(room.getName());
        vo.setLocation(room.getLocation());
        vo.setCapacity(room.getCapacity());
        vo.setEquipment(room.getEquipment());
        vo.setStatus(room.getStatus());
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

    public List<String> getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(List<String> occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }
}
