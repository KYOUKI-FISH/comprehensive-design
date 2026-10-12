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
}
