package com.meetingroom.mapper;

import com.meetingroom.dto.AdminLogQuery;
import com.meetingroom.dto.OperationLogVO;
import com.meetingroom.entity.OperationLog;

import java.util.List;

/**
 * 操作日志表（operation_log）的数据访问。SQL 写在
 * {@code resources/mapper/OperationLogMapper.xml}。
 *
 * <p>★ 只提供 insert 与查询，<b>不提供任何删除／更新方法</b>——
 * NF-14 要求"日志不能被业务界面删除"，审计链一旦可断就不叫审计了。
 * 这不是"暂时没做"，是设计上就不该有。</p>
 */
public interface OperationLogMapper {

    /** 写入一条操作日志，回填自增主键 */
    int insert(OperationLog log);

    /**
     * 条件查询操作日志（接口 #13，NF-14 可追溯）。
     *
     * <p>带出 {@code operatorName}／{@code operatorRole}：日志里存的是操作人账号快照，
     * 姓名和角色要联 {@code user} 表拿。★ 用 LEFT JOIN 而不是 INNER JOIN——
     * 操作人账号后来被删（理论上外键 RESTRICT 挡着，但历史数据可能有）时，
     * 日志本身不该因此消失，否则审计链出现空洞。</p>
     */
    List<OperationLogVO> selectList(AdminLogQuery query);

    /** 同上但只取总数（分页用，与 selectList 共用同一段 where） */
    long countList(AdminLogQuery query);
}
