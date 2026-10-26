package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.entity.OperationLog;
import com.meetingroom.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 操作日志写入服务（NF-14 可审计性）。
 *
 * <p>★ 为什么在 Java 侧再校验一遍枚举：数据库 {@code ck_log_operation_type} 只认 9 个值，
 * 写错会在 INSERT 时抛 ERROR 3819，到那时堆栈已经翻到 Spring 的 DAO 层，排查成本高；
 * 在这里先拦一次，错的值直接得到"不支持的操作类型"这种能看懂的提示。</p>
 *
 * <p>★ 本表只记管理员敏感操作。提交预约**不写**这张表——预约已经在 {@code reservation} 留痕，
 * 重复记一遍既不属 NF-14 范围，也会让日志表混入业务流水（第 8 周 §2.4 定稿口径）。</p>
 */
@Service
public class OperationLogService {

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_DISABLE = "USER_DISABLE";
    public static final String USER_ENABLE = "USER_ENABLE";
    public static final String ROLE_CHANGE = "ROLE_CHANGE";
    public static final String ROOM_CREATE = "ROOM_CREATE";
    public static final String ROOM_UPDATE = "ROOM_UPDATE";
    public static final String ROOM_DELETE = "ROOM_DELETE";
    public static final String ROOM_STATUS = "ROOM_STATUS";
    public static final String BOOKING_FORCE_CANCEL = "BOOKING_FORCE_CANCEL";

    /** 合法操作类型（与 schema.sql 的 ck_log_operation_type 逐值一致） */
    public static final Set<String> ALLOWED_TYPES = new LinkedHashSet<>(java.util.List.of(
            USER_CREATE, USER_DISABLE, USER_ENABLE, ROLE_CHANGE,
            ROOM_CREATE, ROOM_UPDATE, ROOM_DELETE, ROOM_STATUS,
            BOOKING_FORCE_CANCEL));

    /** 合法对象类型（与 ck_log_target_type 一致） */
    public static final Set<String> ALLOWED_TARGETS = new LinkedHashSet<>(java.util.List.of(
            "USER", "ROOM", "BOOKING"));

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 写入一条操作日志。
     *
     * @param operatorId       操作人ID（NOT NULL）
     * @param operatorUsername 操作人账号快照
     * @param operationType    9 种敏感操作之一
     * @param targetType       USER / ROOM / BOOKING
     * @param targetId         对象ID，可为 null
     * @param beforeData       变更前 JSON，可为 null
     * @param afterData        变更后 JSON，可为 null
     * @param remark           备注，可为 null
     */
    public OperationLog log(Long operatorId, String operatorUsername, String operationType,
                            String targetType, Long targetId,
                            String beforeData, String afterData, String remark) {
        if (operatorId == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录");
        }
        if (operatorUsername == null || operatorUsername.isBlank()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "操作人账号不能为空");
        }
        if (!ALLOWED_TYPES.contains(operationType)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "不支持的操作类型：" + operationType + "（仅限 NF-14 列明的管理员敏感操作）");
        }
        if (!ALLOWED_TARGETS.contains(targetType)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "不支持的操作对象类型：" + targetType);
        }

        OperationLog entity = new OperationLog();
        entity.setOperatorId(operatorId);
        entity.setOperatorUsername(operatorUsername);
        entity.setOperationType(operationType);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setBeforeData(blankToNull(beforeData));
        entity.setAfterData(blankToNull(afterData));
        entity.setRemark(blankToNull(remark));
        operationLogMapper.insert(entity);
        return entity;
    }

    /** JSON 列不接受空串，统一转成 null */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
