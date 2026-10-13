package com.meetingroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码摘要器配置（第 6 周：种子数据改为 BCrypt 存储）。
 *
 * <p>本项目只依赖 {@code spring-security-crypto}，不引入
 * {@code spring-boot-starter-security}：后者会自动装配整条安全过滤链，
 * 把本周的登录 / 查询通路一并拦掉，与"只打通环境验证通路"的范围不符。
 * 完整鉴权体系（令牌、权限拦截）属第 9～10 周（FR-01／FR-02）。</p>
 *
 * <p>{@link BCryptPasswordEncoder} 每次编码会混入随机盐，因此同一明文
 * 每次生成的密文都不同；校验只能用 {@code matches(raw, encoded)}，
 * 不能用字符串相等比较。</p>
 */
@Configuration
public class PasswordConfig {

    /** BCrypt 强度（cost）：10 为 Spring Security 默认值，单次校验约几十毫秒。 */
    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
