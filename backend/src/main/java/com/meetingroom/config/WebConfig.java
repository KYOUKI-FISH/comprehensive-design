package com.meetingroom.config;

import com.meetingroom.service.TokenStore;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置（第 9 周）：注册鉴权拦截器。
 *
 * <p>拦截范围覆盖 {@code /api/**}，与 {@link CorsConfig} 的放行路径一致——
 * 两个配置必须对齐，否则会出现"浏览器放行了但拦截器没拦"或反之的空档。
 */
@Configuration
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final TokenStore tokenStore;

    public WebConfig(AuthInterceptor authInterceptor, TokenStore tokenStore) {
        this.authInterceptor = authInterceptor;
        this.tokenStore = tokenStore;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/health");
    }

    /**
     * 定期清理过期会话。
     *
     * <p>令牌校验时虽然也会顺手删掉过期的那一条，但"从没被再次访问"的令牌会一直留在
     * Map 里。定时清理保证内存不随运行时长无限增长。
     *
     * <p>每 5 分钟一次即可：令牌本身 30 分钟就过期，清理频率比 TTL 略快就够了。
     *
     * <p>⚠️ {@code @Scheduled} 方法<strong>不能带参数</strong>，所以 TokenStore 走构造器注入。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void purgeExpiredSessions() {
        int purged = tokenStore.purgeExpired();
        if (purged > 0) {
            // 用 info 而不是 warn：这是正常回收，不是异常
            org.slf4j.LoggerFactory.getLogger(WebConfig.class)
                    .info("清理过期会话 {} 条", purged);
        }
    }
}
