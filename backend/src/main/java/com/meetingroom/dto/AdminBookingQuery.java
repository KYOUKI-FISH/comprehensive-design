package com.meetingroom.dto;

import java.time.LocalDate;

/**
 * 管理端"全部预约"的查询条件（接口 #9，FR-15／FR-19 简化版）。
 *
 * <p>全部字段可空，传哪个按哪个过滤，全不传返回全部。</p>
 *
 * <p>★ 为什么单独建一个类而不是把条件摊在 Controller 方法参数上：
 * Mapper 的 XML 里要用同一组条件写 where 片段，条件一多，方法参数签名会变长到
 * 难以核对；放一个对象里，Service／Mapper／XML 三处引用的是同一个结构，
 * 少传一个参数这种错误编译期就报出来了。</p>
 */
public class AdminBookingQuery {

    /** 按预约人筛（FR-19 多维筛选的一部分，管理端"看某人订了什么"用） */
    private Long userId;
    /** 按会议室筛（管理员四步第 2 步要求） */
    private Long roomId;
    /** 按状态筛：PENDING／ONGOING／FINISHED／CANCELLED */
    private String status;
    /** 按预约日期区间筛（管理员四步第 2 步要求，闭区间） */
    private LocalDate startDate;
    private LocalDate endDate;
    /** 分页：第几页（从 1 起）、每页多少条；都为 null 时不翻页 */
    private Integer pageNum;
    private Integer pageSize;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    /** 偏移量，MyBatis 取不到计算属性，这里给个便利方法供 XML 调用 */
    public int getOffset() {
        if (pageNum == null || pageSize == null || pageNum < 1 || pageSize < 1) {
            return 0;
        }
        return (pageNum - 1) * pageSize;
    }
}
