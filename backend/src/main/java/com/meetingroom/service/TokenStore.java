package com.meetingroom.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话令牌存储（第 9 周；对应 FR-01 登录、NF-04 会话安全、NF-05 账号安全）。
 *
 * <p><b>为什么用服务端随机串而不是 JWT：</b>
 * 第 7—8 周已定口径"服务端随机串 ＋ 服务端失效标记"，不引 JWT 依赖。理由有三：
 * <ol>
 *   <li>本项目要落地的是<strong>登出即失效</strong>（NF-04"会话劫持场景下旧令牌失效"）与
 *       <strong>停用账号立即踢下线</strong>（NF-05）——这两条都要求服务端能主动作废令牌，
 *       无状态 JWT 做不到"立即失效"，还得额外维护黑名单，反而更复杂；</li>
 *   <li>不引新依赖，把工时留给业务功能（本组工时紧张，见《周推进计划_第9周》§2）；</li>
 *   <li>令牌不落库 → <strong>不新增表</strong>，第 8 周的 ER 图与建表脚本保持同源（4 张表不变）。</li>
 * </ol>
 *
 * <p><b>已知取舍（如实记录，不掩饰）：</b>令牌存进程内存，因此
 * ① 后端重启后旧令牌全部失效，需重新登录；② 多实例部署时实例间不共享令牌。
 * 本项目为<strong>本机单实例运行</strong>（第 6 周定稿），预答辩现场为单机演示，
 * 上述两条<strong>不影响任何验收点</strong>。若后续要落表，须走接口变更记录。
 *
 * <p><b>有效期：</b>{@link #TTL_MINUTES} = 30 分钟。对应 NF-04"令牌具备有效期、禁止会话永久有效"。
 * 过期后前端下一次请求会收到 401，应跳回登录页。
 *
 * <p><b>线程安全：</b>用 {@link ConcurrentHashMap}；过期清理用定时任务，
 * 避免"只靠访问时判断过期"导致 Map 无限增长。
 */
@Component
public class TokenStore {

    /** 令牌有效期：30 分钟（NF-04 禁止永久有效） */
    public static final long TTL_MINUTES = 30L;

    /** 令牌摘要条目 */
    public static class Session {
        private final Long userId;
        private final String username;
        private final String role;
        private final LocalDateTime expireAt;

        public Session(Long userId, String username, String role, LocalDateTime expireAt) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.expireAt = expireAt;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public LocalDateTime getExpireAt() {
            return expireAt;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expireAt);
        }
    }

    /** token → 会话 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** 令牌随机源：用 SecureRandom，不用 Random（令牌是可预测就会被伪造） */
    private final SecureRandom random = new SecureRandom();

    /**
     * 为指定用户签发一个新令牌。
     *
     * @param userId   用户ID
     * @param username 登录账号
     * @param role     角色（USER / ADMIN）
     * @return 令牌串（客户端通过 {@code Authorization: Bearer <token>} 带回）
     */
    public String issue(Long userId, String username, String role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(TTL_MINUTES);
        sessions.put(token, new Session(userId, username, role, expireAt));
        return token;
    }

    /**
     * 校验令牌并返回对应会话。
     *
     * @param token 客户端带回的令牌
     * @return 有效会话；令牌不存在、已过期或已登出时返回 {@code null}
     */
    public Session validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            // 过期即删，防止占内存，也保证"旧令牌不能复活"
            sessions.remove(token);
            return null;
        }
        return session;
    }

    /**
     * 登出：立即作废该令牌（NF-04"旧令牌失效"）。
     *
     * @param token 令牌串
     * @return 是否确实作废了一个有效令牌
     */
    public boolean revoke(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return sessions.remove(token) != null;
    }

    /**
     * 作废某用户的全部会话（NF-05"管理员停用账号后，该账号所有活跃会话立刻失效"）。
     *
     * @param userId 用户ID
     * @return 被作废的会话数
     */
    public int revokeAllOf(Long userId) {
        int count = 0;
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().getUserId().equals(userId)) {
                sessions.remove(entry.getKey());
                count++;
            }
        }
        return count;
    }

    /**
     * 清理已过期的会话。定时调用，避免 Map 只增不减。
     *
     * @return 被清理的会话数
     */
    public int purgeExpired() {
        int count = 0;
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                sessions.remove(entry.getKey());
                count++;
            }
        }
        return count;
    }
}
