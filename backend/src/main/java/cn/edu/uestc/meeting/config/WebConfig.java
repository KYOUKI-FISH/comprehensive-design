package cn.edu.uestc.meeting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 相关配置：跨域 + 密码编码器。
 *
 * <p><b>为什么需要跨域配置</b>：本项目采用方案 A2 前后端分离，前端由 Vite 开发服务器
 * 在 {@code http://localhost:5173} 提供，后端在 {@code http://localhost:8080}，
 * 端口不同即跨域。这是《技术选型报告》里"选 A2 会踩的坑"之一，此处先按最小可用配好，
 * 第 7 周接口设计阶段再收紧到具体域名与允许的方法。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 前端开发服务器地址（Vite 默认端口），见 frontend/vite.config.ts */
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(FRONTEND_ORIGIN, "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 密码编码器：BCrypt（cost=10，与 database/02_seed.sql 中的种子密文一致）。
     * 明文比对永不落地——数据库里只存密文。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
