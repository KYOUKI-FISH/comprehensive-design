package com.meetingroom.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置（架构 A2 前后端分离的必要配置）。
 *
 * <p>《技术选型报告》已预测"前后端分离最容易踩的坑是跨域"，故骨架阶段就先行配置：
 * 后端 8080、前端 Vite 开发服务器默认 5173，两个不同源，浏览器会拦请求。</p>
 *
 * <p>前端端口如非 5173，改这里或运行时用 {@code --port} 指定即可。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
