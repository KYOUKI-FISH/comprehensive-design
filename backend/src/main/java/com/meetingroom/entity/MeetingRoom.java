package com.meetingroom.entity;

import java.time.LocalDateTime;

/**
 * 会议室实体 —— 对应建表脚本 {@code meeting_room} 表（DFD 数据存储 D2 会议室信息）。
 */
public class MeetingRoom {

    /** 会议室ID */
    private Long id;
    /** 会议室名称（唯一） */
    private String name;
    /** 位置，FR-05 按条件查询的筛选字段 */
    private String location;
    /** 可容纳人数，FR-05 按条件查询的筛选字段 */
    private Integer capacity;
    /** 设备描述（展示用） */
    private String equipment;
    /** 启用状态：ENABLED=启用 / DISABLED=停用（FR-04、问题 P03） */
    private String status;
    /** 备注 */
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
