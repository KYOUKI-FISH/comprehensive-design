-- =============================================================================
-- 智能会议室管理系统 V1.0 —— 建表脚本（第 6 周工程骨架）
-- 小组：第 4 组 ｜ 数据库：MySQL 8.0.x ｜ 编码：utf8mb4
-- 对应第 5 周 DFD 的数据存储：D1 用户信息 / D2 会议室信息 / D3 预约记录
-- -----------------------------------------------------------------------------
-- 口径声明（与需求清单 V1.1、DFD、用例规约一致，勿改）：
--   1. 系统采用【方案 A：自由时间段预约】——用户自定义开始/结束时间。
--   2. 时间区间统一按【半开区间 [start, end)】理解：
--        判定冲突 <=> start_new < end_old AND end_new > start_old
--        首尾相接（end_new == start_old 或 start_new == end_old）不算冲突。
--      ⚠ 本脚本暂不建"防重复预订"的约束：重叠区间无法用普通 UNIQUE 键表达
--        （UNIQUE(room_id, reserve_date, start_time, end_time) 只能挡住完全相同的
--         一条记录，挡不住 09:00-10:00 与 09:30-10:30 这类部分重叠）。
--        FR-09 冲突检测由应用层在事务内查询判定（行锁 + 唯一性校验），
--        属第 9~10 周开发范围，本周只建表。
--   3. 预约状态统一命名：待开始 / 进行中 / 已结束 / 已取消
--        （库内存储英文码，见 reservation.status 注释）。
--   4. 本学期暂不实现 9 项：人脸识别、物联网设备控制、3D 场景、智能推荐、
--      重复预约（指周期性/批量重复预约）、多级审批、消息推送、
--      统一身份认证对接、移动端原生应用。本脚本不得为其预留表或字段。
-- -----------------------------------------------------------------------------
-- ⚠⚠ 本脚本会先删除同名数据库再重建，仅供本机开发库初始化使用 ⚠⚠
--     执行命令（Windows 命令行 / PowerShell 均可）：
--       mysql -u root -p --default-character-set=utf8mb4 < database/01_schema.sql
-- =============================================================================

DROP DATABASE IF EXISTS `meeting_room`;
CREATE DATABASE `meeting_room`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `meeting_room`;

-- =============================================================================
-- D1 用户信息
-- DFD 对应：P1 验证登录（账号、密码 → 登录结果、角色）／P5 管理用户
-- =============================================================================
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID（主键）',
    `username`    VARCHAR(50)  NOT NULL                 COMMENT '登录账号，唯一',
    `password`    VARCHAR(100) NOT NULL                 COMMENT '密码密文（BCrypt，60 字符）',
    `real_name`   VARCHAR(50)      NULL                 COMMENT '姓名',
    `role`        VARCHAR(20)  NOT NULL                 COMMENT '角色：USER=普通用户，ADMIN=管理员（FR-02 角色区分）',
    `status`      TINYINT      NOT NULL DEFAULT 1       COMMENT '账号状态：1=启用，0=停用（FR-14 管理用户账号）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'D1 用户信息';

-- =============================================================================
-- D2 会议室信息
-- DFD 对应：P2 查询会议室（查询条件 → 会议室列表）／P6 管理会议室
-- =============================================================================
CREATE TABLE `meeting_room` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会议室 ID（主键）',
    `name`        VARCHAR(50)  NOT NULL                 COMMENT '会议室名称，唯一（如"主楼 A301"）',
    `location`    VARCHAR(100) NOT NULL                 COMMENT '位置（楼栋/楼层），FR-05 查询条件之一',
    `capacity`    INT          NOT NULL                 COMMENT '容纳人数，FR-05 查询条件之一',
    `status`      TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：1=启用，0=停用（FR-04 启用和停用；停用后前台查询不返回，FR-06）',
    `remark`      VARCHAR(200)     NULL                 COMMENT '备注（如"靠窗、有白板"）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_name` (`name`),
    KEY `idx_room_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'D2 会议室信息';

-- =============================================================================
-- D3 预约记录
-- DFD 对应：P3 提交预约／P4 维护我的预约／P7 处理预约
-- 时间语义：reserve_date + start_time + end_time 三者合起来表示半开区间
--           [reserve_date start_time, reserve_date end_time)
-- =============================================================================
CREATE TABLE `reservation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '预约 ID（主键，即 DFD 中的"预约编号"）',
    `room_id`       BIGINT       NOT NULL                 COMMENT '会议室 ID → meeting_room.id',
    `user_id`       BIGINT       NOT NULL                 COMMENT '预约人 ID → user.id',
    `reserve_date`  DATE         NOT NULL                 COMMENT '预约日期（FR-08：禁止跨天，故日期只存一个）',
    `start_time`    TIME         NOT NULL                 COMMENT '开始时间（含）',
    `end_time`      TIME         NOT NULL                 COMMENT '结束时间（不含）—— 半开区间右端',
    `purpose`       VARCHAR(200)     NULL                 COMMENT '用途说明（FR-07 必填字段）',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '预约状态：PENDING=待开始，ONGOING=进行中，FINISHED=已结束，CANCELLED=已取消',
    `cancel_reason` VARCHAR(200)     NULL                 COMMENT '取消/强制取消原因（管理员处理异常预约须填写，UC-A05）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_res_room_date` (`room_id`, `reserve_date`),
    -- FR-09 冲突检测查询走这个索引：WHERE room_id=? AND reserve_date=? AND start_time<? AND end_time>?
    KEY `idx_res_room_date_time` (`room_id`, `reserve_date`, `start_time`, `end_time`),
    KEY `idx_res_user` (`user_id`),
    KEY `idx_res_status` (`status`),
    CONSTRAINT `fk_res_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`),
    CONSTRAINT `fk_res_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = 'D3 预约记录';

-- =============================================================================
-- 建表结果自检（应返回 3 行）
-- =============================================================================
SELECT TABLE_NAME AS `表名`, TABLE_COMMENT AS `说明`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meeting_room'
ORDER BY TABLE_NAME;
