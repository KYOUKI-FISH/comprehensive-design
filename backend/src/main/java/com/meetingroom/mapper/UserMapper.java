package com.meetingroom.mapper;

import com.meetingroom.entity.User;
import org.apache.ibatis.annotations.Param;

/**
 * 用户表（D1）数据访问。SQL 写在 {@code resources/mapper/UserMapper.xml}。
 */
public interface UserMapper {

    /**
     * 按登录账号查用户。登录通路使用（DFD 加工 P1 验证登录）。
     *
     * @param username 登录账号
     * @return 用户；不存在时返回 {@code null}
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 按用户ID查用户。第 9 周鉴权拦截器回查账号状态时使用
     * （NF-05「管理员停用账号后，该账号所有活跃会话立刻失效」——
     * 令牌本身不查库就发现不了账号已被停用）。
     *
     * @param id 用户ID
     * @return 用户；不存在时返回 {@code null}
     */
    User selectById(@Param("id") Long id);
}
