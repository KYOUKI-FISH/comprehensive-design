package com.meetingroom.mapper;

import com.meetingroom.entity.OperationLog;

/**
 * 操作日志表（operation_log）的数据访问。SQL 写在
 * {@code resources/mapper/OperationLogMapper.xml}。
 *
 * <p>只提供 insert：日志写入后不提供业务删除（NF-14「日志不能被业务界面删除」）。</p>
 */
public interface OperationLogMapper {

    /** 写入一条操作日志，回填自增主键 */
    int insert(OperationLog log);
}
