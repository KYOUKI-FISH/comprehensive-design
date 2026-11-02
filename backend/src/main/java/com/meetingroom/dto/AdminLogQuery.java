package com.meetingroom.dto;

import java.time.LocalDate;

/**
 * 操作日志查询条件（接口 #13，NF-14）。
 *
 * <p>与 {@link AdminBookingQuery} 同构：字段全部可空，传哪个按哪个过滤。</p>
 *
 * <p>★ {@code startDate}／{@code endDate} 按 {@code operated_at} 的日期部分比较，
 * 传 endDate 时含当天全天（XML 里用 {@code < endDate + 1 天}，不用 {@code <=}，
 * 否则 2026-11-04 当天的日志会因为 {@code 09:30 > 2026-11-04 00:00} 而被漏掉）。</p>
 */
public class AdminLogQuery {

    private Long operatorId;
    /** 9 种敏感操作之一，如 BOOKING_FORCE_CANCEL */
    private String operationType;
    /** USER／ROOM／BOOKING */
    private String targetType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer pageNum;
    private Integer pageSize;

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
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

    public int getOffset() {
        if (pageNum == null || pageSize == null || pageNum < 1 || pageSize < 1) {
            return 0;
        }
        return (pageNum - 1) * pageSize;
    }
}
