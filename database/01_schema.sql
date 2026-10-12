-- =============================================================================
-- 智能会议室管理系统 V1.0 —— 建表脚本（第 6 周 · 工程骨架）
-- 文件    : database/01_schema.sql
-- 负责人  : 虞业宸（2025091202009，Git: yycyuyechen）
-- 对应成果: 第 5 周 DFD 数据存储 —— D1 用户信息 / D2 会议室信息 / D3 预约记录
-- 对应需求: FR-01～FR-14（用户登录与角色区分、会议室管理、查询、预约申请与冲突检测、我的预约）
-- 环境要求: MySQL 8.0.16 及以上（CHECK 约束自 8.0.16 起才被真正执行）
-- 执行方式: mysql -u root -p < database/01_schema.sql
--
-- ⚠️ 本脚本会 DROP 并重建三张表，会删除这三张表中的全部数据，仅用于初始化 / 重置。
--    重新灌入演示数据请继续执行 database/02_seed.sql。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 【核心业务规则 · 半开区间 [start, end)】（与需求清单 V1.1 FR-09 一致）
--   一条预约占用的时间区间记作 [start_time, end_time)：含开始时刻、不含结束时刻。
--   冲突判定：新预约 [s_new, e_new) 与已有预约 [s_old, e_old)
--             当 s_new < e_old 且 e_new > s_old 时，判为冲突。
--   ★ 首尾相接不算冲突：09:00-10:00 与 10:00-11:00 可以同时存在。
--   冲突检测本身在业务层实现（FR-09，第 9～10 周），用数据库事务 + 行锁保证并发下不超订；
--   本脚本只负责提供支撑该查询的索引 idx_reservation_room_time 与首尾相接的种子数据。
-- -----------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS `meeting_room`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `meeting_room`;

-- 按外键依赖倒序删除，保证脚本可重复执行
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `meeting_room`;
DROP TABLE IF EXISTS `user`;


-- =============================================================================
-- D1 用户信息（user）
-- 数据流来源：P1 验证登录（账号、密码 → 登录结果、角色）、P5 管理用户
-- =============================================================================
CREATE TABLE `user` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键',
  `username`   VARCHAR(50)  NOT NULL                COMMENT '登录账号（唯一）',
  `password`   VARCHAR(100) NOT NULL                COMMENT '登录密码。★骨架阶段为明文，仅用于打通登录通路；第 9～10 周业务开发时替换为 BCrypt 摘要并迁移种子数据',
  `real_name`  VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '姓名',
  `role`       VARCHAR(20)  NOT NULL                COMMENT '角色：USER=普通用户 / ADMIN=管理员（FR-02 角色区分与权限控制）',
  `phone`      VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE=正常 / DISABLED=停用（FR-14 管理员管理用户账号）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role` (`role`),
  CONSTRAINT `ck_user_role` CHECK (`role` IN ('USER', 'ADMIN')),
  CONSTRAINT `ck_user_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'D1 用户信息表（普通用户 + 管理员）';


-- =============================================================================
-- D2 会议室信息（meeting_room）
-- 数据流来源：P2 查询会议室（查询条件 → 会议室列表）、P6 管理会议室
-- =============================================================================
CREATE TABLE `meeting_room` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID，主键',
  `name`       VARCHAR(100) NOT NULL                COMMENT '会议室名称（唯一）',
  `location`   VARCHAR(100) NOT NULL                COMMENT '位置（如"逸夫楼A座1层"），FR-05 按条件查询的筛选字段',
  `capacity`   INT UNSIGNED NOT NULL                COMMENT '可容纳人数，FR-05 按条件查询的筛选字段',
  `equipment`  VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '设备描述（投影仪、白板、视频会议终端等），仅作展示',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态：ENABLED=启用 / DISABLED=停用（FR-04、问题 P03）',
  `remark`     VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_name` (`name`),
  KEY `idx_room_status` (`status`),
  CONSTRAINT `ck_room_capacity` CHECK (`capacity` > 0),
  CONSTRAINT `ck_room_status` CHECK (`status` IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'D2 会议室信息表；停用后前台查询须过滤掉（FR-06，问题 P03）';


-- =============================================================================
-- D3 预约记录（reservation）
-- 数据流来源：P3 提交预约、P4 维护我的预约、P7 处理预约；P2 查询会议室时读本表出"当日占用时段"
-- =============================================================================
CREATE TABLE `reservation` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约ID，主键',
  `reservation_no`  VARCHAR(32)  NOT NULL                COMMENT '预约编号（对外展示，唯一），形如 MR20261012001',
  `room_id`         BIGINT UNSIGNED NOT NULL             COMMENT '会议室ID → meeting_room.id（DFD 数据存储 D2）',
  `user_id`         BIGINT UNSIGNED NOT NULL             COMMENT '预约人ID → user.id（DFD 数据存储 D1）',
  `reserve_date`    DATE         NOT NULL                COMMENT '预约日期。与 start_time 冗余存储，便于按日查询与"禁止跨天"校验（FR-08）',
  `start_time`      DATETIME     NOT NULL                COMMENT '开始时间（★含本时刻，半开区间左闭）',
  `end_time`        DATETIME     NOT NULL                COMMENT '结束时间（★不含本时刻，半开区间右开）',
  `purpose`         VARCHAR(255) NOT NULL                COMMENT '用途（FR-07 提交预约的必填字段）',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '预约状态：PENDING=待开始 / ONGOING=进行中 / FINISHED=已结束 / CANCELLED=已取消',
  `cancel_reason`   VARCHAR(255) DEFAULT NULL            COMMENT '取消原因。管理员处理异常预约须填写且不少于 5 个字符（UC-A05）',
  `operator_id`     BIGINT UNSIGNED DEFAULT NULL         COMMENT '处理该预约的管理员ID，仅"处理异常预约"时写入',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_no` (`reservation_no`),
  -- 冲突检测（FR-09）与查询页"当日占用时段"（DFD P2）都走这个联合索引
  KEY `idx_reservation_room_time` (`room_id`, `start_time`, `end_time`),
  KEY `idx_reservation_user` (`user_id`),
  KEY `idx_reservation_date_status` (`reserve_date`, `status`),
  -- 时间边界规则（FR-08）：结束须晚于开始、禁止跨天、单次不超过 8 小时
  -- 注：若你的 MySQL 对 CHECK 中的函数有限制而导致建表失败，可删掉下面三条时间类 CONSTRAINT，
  --     不影响系统运行——FR-08 本身还要靠前后端双重校验（第 9～10 周），数据库约束只是最后一道防线。
  CONSTRAINT `ck_reservation_time_order` CHECK (`end_time` > `start_time`),
  CONSTRAINT `ck_reservation_same_day`   CHECK (DATE(`start_time`) = DATE(`end_time`)),
  CONSTRAINT `ck_reservation_max_8h`     CHECK (TIMESTAMPDIFF(MINUTE, `start_time`, `end_time`) <= 480),
  CONSTRAINT `ck_reservation_status`     CHECK (`status` IN ('PENDING', 'ONGOING', 'FINISHED', 'CANCELLED')),
  CONSTRAINT `fk_reservation_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`),
  CONSTRAINT `fk_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'D3 预约记录表；同会议室同一时段只允许一条有效预约（半开区间判定）';


-- =============================================================================
-- 自带验证：建表完成后核对三张表是否就位
-- =============================================================================
SELECT TABLE_NAME AS `已建表`, TABLE_COMMENT AS `说明`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meeting_room'
ORDER BY TABLE_NAME;
