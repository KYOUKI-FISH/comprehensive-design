package com.meetingroom.dto;

import java.time.LocalDateTime;

/**
 * 操作日志视图（接口 #13 出参元素，NF-14）。
 *
 * <p>★ 字段按需求清单 §接口 #13 逐项对齐：{@code id}、{@code operatorName}、
 * {@code operatorRole}、{@code operationType}、{@code targetType}、{@code targetId}、
 * {@code detail}、{@code ip}、{@code createdAt}。</p>
 *
 * <p>其中 {@code ip} 在建表脚本里<b>没有对应列</b>（第 8 周定稿的 operation_log 只有
 * 操作人／时间／类型／前后数据／备注）。本 VO 保留该字段并固定返回 null，
 * 是为了让前端按接口清单写好的表格不必因缺字段而特殊处理；
 * <b>不为了填它去改表结构</b>——第 11 周不改结构的约束本周就要开始守。</p>
 */
public class OperationLogVO {

    private Long id;
    /** 操作人账号快照（日志表里存的原值，操作人后来改名也不影响历史记录） */
    private String operatorUsername;
    /** 操作人姓名（联表 user.real_name） */
    private String operatorName;
    /** 操作人当前角色（USER／ADMIN），联表取得 */
    private String operatorRole;
    private String operationType;
    private String targetType;
    private Long targetId;
    /** 变更前数据（JSON 串原样返回，前端按需展开） */
    private String beforeData;
    /** 变更后数据（JSON 串原样返回） */
    private String afterData;
    /** 写入日志时附带的备注原文（SQL 里 remark 列映射到此，Service 用它拼 detail） */
    private String remark;
    /**
     * 人类可读的变更摘要，如"预约 MR20261102005：PENDING → CANCELLED，原因：…"。
     * 由 Service 从 beforeData／afterData／remark 拼出，前端直接显示，不必自己解析 JSON。
     */
    private String detail;
    /** 见类注释：建表脚本无 ip 列，固定为 null */
    private String ip;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public void setOperatorUsername(String operatorUsername) {
        this.operatorUsername = operatorUsername;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public void setOperatorRole(String operatorRole) {
        this.operatorRole = operatorRole;
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

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(String beforeData) {
        this.beforeData = beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
