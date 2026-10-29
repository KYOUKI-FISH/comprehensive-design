package com.meetingroom.mapper;

import com.meetingroom.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    // -------------------------------------------------------------------------
    // 管理端用户管理（第 10 周，李知渔）。接口 #12。
    // -------------------------------------------------------------------------

    /**
     * 用户列表（接口 #12 GET，FR-14）。
     *
     * <p>★ 返回的 {@code User} 实体会带回 password 摘要，**绝不能直接序列化成响应**
     * ——Service 层统一转成不含口令的 VO，这条在自测里单独验一次。</p>
     *
     * @param status 可空，ACTIVE／DISABLED
     * @param role   可空，USER／ADMIN
     */
    List<User> selectList(@Param("status") String status,
                          @Param("role") String role);

    /** 按账号查（建号查重用）。与 selectByUsername 的区别：这条不加 LIMIT 1 的语义假设，只查存在性 */
    User selectByUsernameForCheck(@Param("username") String username);

    /** 新增用户，回填自增主键 */
    int insert(User user);

    /**
     * 更新用户的可变字段（接口 #12 PUT）。
     *
     * <p>★ 只更新非 null 字段：管理员可能只想改电话，不该把姓名一起清掉。
     * 用 MyBatis 的 {@code <set>} + 动态 if 实现，空对象更新会被 Service 提前拦下。</p>
     */
    int updateSelective(User user);

    /**
     * 统计"可用状态的管理员"数量（降级前置校验用）。
     *
     * <p>★ 为什么要这道校验：接口清单只写了"不能停用当前登录的管理员账号"，
     * 但把最后一个管理员<b>降级</b>成普通用户，效果和停用一样——管理端再也进不去。
     * 只堵停用不堵降级，等于留了个后门。</p>
     */
    long countActiveAdmins();
}
