package com.meetingroom.entity;

import java.time.LocalDateTime;

/**
 * 操作日志实体 —— 对应建表脚本 {@code operation_log} 表（NF-14 可审计性）。
 *
 * <p>★ 只记**管理员敏感操作**（9 种，见 {@code ck_log_operation_type}）。
 * 登录、提交预约、用户自行取消预约**不进这张表**：那些是业务数据，
 * 预约本身已在 {@code reservation} 表留痕（第 8 周 §2.4 的口径）。
 * 写错枚举值会被数据库 CHECK 直接拒绝（ERROR 3819），所以 Service 侧先校验一遍。</p>
 */
public class OperationLog {

    private Long id;
    /** 操作人 → user.id（NOT NULL） */
    private Long operatorId;
    /** 操作人账号快照：用户改名/停用后仍能追溯"当时是谁操作的" */
    private String operatorUsername;
    /** 操作类型（9 种敏感操作之一） */
    private String operationType;
    /** 操作对象类型：USER / ROOM / BOOKING */
    private String targetType;
    /** 操作对象ID，可为 null（如删除后对象已不存在） */
    private Long targetId;
    /** 变更前数据快照（JSON 字符串），新增类操作为 null */
    private String beforeData;
    /** 变更后数据快照（JSON 字符串），删除类操作为 null */
    private String afterData;
    /** 操作时间 */
    private LocalDateTime operatedAt;
    /** 补充说明 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public void setOperatorUsername(String operatorUsername) {
        this.operatorUsername = operatorUsername;
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

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }

    public void setOperatedAt(LocalDateTime operatedAt) {
        this.operatedAt = operatedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
