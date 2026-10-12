package cn.edu.uestc.meeting;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能会议室管理系统 V1.0 —— 后端启动类。
 *
 * <p>小组：第 4 组 ｜ 架构：A2 前后端分离（本进程只提供 REST API，不渲染页面）
 *
 * <p>本周（第 6 周）范围：只打通"登录 + 查询会议室"两条<b>环境验证通路</b>，
 * 用于证明环境能跑、库能连、表能查；预约提交、冲突检测、管理等业务属第 9～10 周。
 * 启动步骤见仓库根 README 的「快速启动」章节。
 */
@SpringBootApplication
@MapperScan("cn.edu.uestc.meeting.mapper")
public class MeetingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingApplication.class, args);
    }
}
