package com.meetingroom;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能会议室管理系统 V1.0 —— 后端启动类。
 *
 * <p>本周（第 6 周）职责仅为「环境验证通路」：证明环境能跑、库能连、表能查。
 * 提供的接口只有登录与会议室查询两个（外加一个健康检查），
 * 预约提交、冲突检测、管理等完整业务按计划在第 9～10 周实现。</p>
 *
 * <p>负责人：虞业宸（2025091202009，Git: yycyuyechen）</p>
 */
@SpringBootApplication
@MapperScan("com.meetingroom.mapper")
public class MeetingRoomApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingRoomApplication.class, args);
    }
}
