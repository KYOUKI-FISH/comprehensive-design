package cn.edu.uestc.meeting.entity;

import java.time.LocalDateTime;

/**
 * 会议室实体 —— 对应 DFD 数据存储 <b>D2 会议室信息</b>，库表 {@code meeting_room}。
 *
 * <p>用途：P2 查询会议室（FR-05 按条件查询、FR-06 显示可用状态）、P6 管理会议室。
 *
 * <p>骨架阶段本类直接作为查询接口出参使用；第 7 周接口设计时若出参需要裁剪或补充
 * 派生字段（如"当日可用时段"），再拆出 VO。
 */
public class MeetingRoom {

    private Long id;

    /** 会议室名称（唯一），如"主楼 A301" */
    private String name;

    /** 位置（楼栋/楼层），FR-05 查询条件之一 */
    private String location;

    /** 容纳人数，FR-05 查询条件之一 */
    private Integer capacity;

    /** 状态：1=启用，0=停用（FR-04；停用后不出现在前台查询结果，FR-06） */
    private Integer status;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
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
