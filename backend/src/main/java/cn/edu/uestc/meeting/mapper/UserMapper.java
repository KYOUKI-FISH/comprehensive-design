package cn.edu.uestc.meeting.mapper;

import cn.edu.uestc.meeting.entity.User;
import org.apache.ibatis.annotations.Param;

/**
 * D1 用户信息的数据访问接口（SQL 写在 resources/mapper/UserMapper.xml）。
 *
 * <p>骨架阶段只实现"按账号查用户"——登录通路唯一需要的方法。
 * 管理用户（FR-14）所需的增删改查属第 9～10 周。
 */
public interface UserMapper {

    /**
     * 按登录账号查询用户（含密码密文，供登录校验）。
     *
     * @param username 登录账号
     * @return 匹配的用户；不存在时返回 {@code null}
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 统计指定状态的用户数量——仅用于 /api/health 连通性自检，不做业务用途。
     */
    int countByStatus(@Param("status") Integer status);
}
