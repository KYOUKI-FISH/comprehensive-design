-- =============================================================================
-- 智能会议室管理系统 V1.0 —— 建表脚本（第 7—8 周 · 数据库定稿 / 拆分版 · 结构部分）
-- 文件    : database/01_schema.sql
-- 负责人  : 虞业宸（2025091202009，Git: yycyuyechen）
--
-- ★ 本文件是"结构"拆分版：只建库、建表、加约束与索引，**不灌数据**。
--   第 7—8 周的正式交付物是**自包含版** docs/week07-08/schema.sql
--   （建库+建表+约束+索引+初始化数据在同一个文件里，供课堂"干净库一次跑通"演示）。
--   本文件与 02_seed.sql 与那份自包含版**同源同表**，供日常开发单独重置结构 / 单独重灌数据使用。
--   改表结构时三处必须同步改：docs/week07-08/schema.sql、本文件、02_seed.sql。
--
-- 对应文档: docs/week07-08/02_数据库与接口设计.md（表结构、状态取值、索引与约束逐条对应）
-- 对应需求: FR-01～FR-20 / NF-03、NF-05、NF-07、NF-08、NF-13、NF-14
-- 环境要求: MySQL 8.0.16 及以上（CHECK 约束自 8.0.16 起才被真正执行）
-- 执行方式: mysql -u root -p --default-character-set=utf8mb4 < database/01_schema.sql
--           然后执行 database/02_seed.sql 灌入演示数据
--
-- ⚠️ 本脚本会 DROP 并重建 4 张表，会清空这 4 张表的全部数据，仅用于初始化 / 重置。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 【核心业务规则 · 半开区间 [start, end)】（与需求清单 V1.1 FR-09 同源）
--   一条预约占用的时间区间记作 [start_time, end_time)：含开始时刻、不含结束时刻。
--   冲突判定：新预约 [s_new, e_new) 与已有有效预约 [s_old, e_old)
--             当 s_new < e_old 且 e_new > s_old 时，判为冲突。
--   ★ 首尾相接不算冲突：09:00-10:00 与 10:00-11:00 可以同时存在。
--   支撑查询（第 9 周实现，本脚本只提供索引）：
--       SELECT 1 FROM reservation
--        WHERE room_id = ? AND reserve_date = ?
--          AND status IN (0, 1)          -- 只有"待开始 / 进行中"才占位
--          AND start_time < ?            -- 新预约的 end_time
--          AND end_time   > ?            -- 新预约的 start_time
--        LIMIT 1;
--   索引 idx_reservation_room_time(room_id, reserve_date, start_time, end_time)
--   覆盖上述查询的等值列与范围列。并发兜底靠事务 + 行锁（NF-07）。
-- -----------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS `meeting_room`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `meeting_room`;

-- 按外键依赖倒序删除：operation_log → reservation → meeting_room → user
DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `meeting_room`;
DROP TABLE IF EXISTS `user`;


-- =============================================================================
-- 表 1／4：user　用户表（普通用户 + 管理员）
-- FR-01 登录、FR-02 角色区分、FR-14 管理用户、NF-05 密码不得明文
-- role 以枚举列承载，不建 role 表（需求清单无"角色管理"功能）
-- =============================================================================
CREATE TABLE `user` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键，自增',
  `username`   VARCHAR(50)     NOT NULL                COMMENT '登录账号，全局唯一（FR-01）；管理员建号时校验重复（FR-14）',
  `password`   VARCHAR(100)    NOT NULL                COMMENT '登录密码的 BCrypt 摘要（cost=10，固定 60 字符）。禁止存明文（NF-05）',
  `real_name`  VARCHAR(50)     NOT NULL                COMMENT '姓名，用于页面展示与操作人留痕',
  `role`       VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色枚举：USER=普通用户 / ADMIN=管理员（FR-02）',
  `phone`      VARCHAR(20)     DEFAULT NULL            COMMENT '联系电话，可空；演示数据不得用真实手机号（NF-06）',
  `status`     VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE=正常 / DISABLED=已停用不可登录（FR-14、NF-05）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role` (`role`),
  KEY `idx_user_status` (`status`),
  CONSTRAINT `ck_user_role`   CHECK (`role`   IN ('USER', 'ADMIN')),
  CONSTRAINT `ck_user_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户表（普通用户 + 管理员）；role/status 为枚举列，不建关联表';


-- =============================================================================
-- 表 2／4：meeting_room　会议室表
-- FR-03 会议室管理、FR-04 启用停用、FR-05 条件查询、FR-06 显示可用状态
-- UNIQUE(name) 支撑 FR-03「同名会议室拒绝重复新增」
-- 定位字段为 location（位置），与第 6 周既有后端实体 MeetingRoom 及 MeetingRoomMapper.xml 同源
-- =============================================================================
CREATE TABLE `meeting_room` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID，主键，自增',
  `name`       VARCHAR(100)    NOT NULL                COMMENT '会议室名称（唯一），FR-03 同名拒绝重复新增',
  `location`   VARCHAR(100)    NOT NULL                COMMENT '位置，如"逸夫楼A座1层"；FR-05 按位置或名称模糊查询的筛选字段',
  `capacity`   INT UNSIGNED    NOT NULL                COMMENT '可容纳人数，FR-05 按容量筛选的比较字段',
  `equipment`  VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '设备描述（投影仪、白板、视频会议终端等），仅展示',
  `status`     VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '会议室状态：ENABLED=可用 / DISABLED=停用（FR-04）；停用后不作为新预约选项（FR-06）',
  `remark`     VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_name` (`name`),
  KEY `idx_room_status` (`status`),
  KEY `idx_room_capacity` (`capacity`),
  CONSTRAINT `ck_room_status`   CHECK (`status` IN ('ENABLED', 'DISABLED')),
  CONSTRAINT `ck_room_capacity` CHECK (`capacity` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '会议室表；名称唯一（FR-03），停用后不作为新预约选项（FR-06）';


-- =============================================================================
-- 表 3／4：reservation　预约记录表
-- FR-07 提交、FR-08 时间规则、FR-09 冲突检测、FR-10 冲突提示、FR-11 我的预约、
-- FR-12 修改、FR-13 取消、FR-15 全部预约、FR-16 处理异常、NF-08 状态流转
--
-- 时间存法：start_time / end_time 为 DATETIME NOT NULL，占用区间 [start_time, end_time)。
--   reserve_date 冗余存 DATE，由 ck_reservation_date_match 保证恒等于 DATE(start_time)。
--   用 DATETIME 而非 TIMESTAMP：存"墙上时间"、不做时区转换，本机单时区运行更稳。
-- 状态存法：TINYINT，四态 0 待开始 / 1 进行中 / 2 已结束 / 3 已取消（NF-08，不用任务书示例的三态）。
-- 删会议室：fk_reservation_room 为 ON DELETE RESTRICT，有预约就删不掉（FR-03）。
-- =============================================================================
CREATE TABLE `reservation` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约主键ID，自增；对外不暴露，对外用 reservation_no',
  `reservation_no`     VARCHAR(32)     NOT NULL                COMMENT '预约编号，全局唯一，对外展示（FR-07）。规则：BK + yyyyMMdd + 4位序号，如 MR202611020001',
  `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '预约人ID → user(id)，ON DELETE RESTRICT（FR-11）',
  `room_id`        BIGINT UNSIGNED NOT NULL                COMMENT '会议室ID → meeting_room(id)，ON DELETE RESTRICT（FR-03）',
  `reserve_date`   DATE            NOT NULL                COMMENT '预约日期，恒等于 DATE(start_time)；用于按日查询与占用日历（FR-17、FR-20）',
  `start_time`     DATETIME        NOT NULL                COMMENT '开始时间，★含本时刻（半开区间左闭）；不得早于当前时间（FR-08）',
  `end_time`       DATETIME        NOT NULL                COMMENT '结束时间，★不含本时刻（半开区间右开）；须晚于 start_time（FR-08）',
  `topic`          VARCHAR(100)    NOT NULL                COMMENT '会议主题，必填（FR-07）',
  `attendee_count` INT UNSIGNED    NOT NULL                COMMENT '参会人数，必填，须大于 0（FR-07）',
  `contact`        VARCHAR(50)     NOT NULL                COMMENT '联系人，必填（FR-07）',
  `purpose`        VARCHAR(255)    DEFAULT NULL            COMMENT '用途补充说明，选填',
  `status`         VARCHAR(20)         NOT NULL DEFAULT 'PENDING'       COMMENT '预约状态：PENDING 待开始 / ONGOING 进行中 / FINISHED 已结束 / CANCELLED 已取消（NF-08 四态）',
  `cancel_reason`  VARCHAR(200)    DEFAULT NULL            COMMENT '取消原因；管理员强制取消异常预约时必填（FR-16）',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_no` (`reservation_no`),
  KEY `idx_reservation_room_time` (`room_id`, `reserve_date`, `start_time`, `end_time`),
  KEY `idx_reservation_user_date` (`user_id`, `reserve_date`),
  KEY `idx_reservation_date_status` (`reserve_date`, `status`),
  CONSTRAINT `ck_reservation_time_order` CHECK (`end_time` > `start_time`),
  CONSTRAINT `ck_reservation_same_day`   CHECK (DATE(`start_time`) = DATE(`end_time`)),
  CONSTRAINT `ck_reservation_max_8h`     CHECK (TIMESTAMPDIFF(MINUTE, `start_time`, `end_time`) <= 480),
  CONSTRAINT `ck_reservation_date_match` CHECK (`reserve_date` = DATE(`start_time`)),
  CONSTRAINT `ck_reservation_status`     CHECK (`status` IN ('PENDING', 'ONGOING', 'FINISHED', 'CANCELLED')),
  CONSTRAINT `ck_reservation_attendee`   CHECK (`attendee_count` > 0),
  CONSTRAINT `fk_reservation_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '预约记录表；时间按半开区间 [start_time,end_time) 判冲突（FR-09），状态四态（NF-08）';


-- =============================================================================
-- 表 4／4：operation_log　管理员敏感操作日志表
-- NF-14 可审计性（必做项）：记录操作人、账号、时间、类型、变更前后数据
-- 表上不设删除标志、不提供删除接口——日志"不能被业务界面删除"（NF-14）
-- =============================================================================
CREATE TABLE `operation_log` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键，自增',
  `operator_id`       BIGINT UNSIGNED NOT NULL                COMMENT '操作人ID → user(id)，ON DELETE RESTRICT',
  `operator_username` VARCHAR(50)     NOT NULL                COMMENT '操作人账号（冗余快照），改名/停用后仍可追溯（NF-14）',
  `operation_type`    VARCHAR(50)     NOT NULL                COMMENT '操作类型：USER_CREATE / USER_DISABLE / USER_ENABLE / ROLE_CHANGE / ROOM_CREATE / ROOM_UPDATE / ROOM_DELETE / ROOM_STATUS / BOOKING_FORCE_CANCEL',
  `target_type`       VARCHAR(30)     NOT NULL                COMMENT '操作对象类型：USER / ROOM / BOOKING',
  `target_id`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '操作对象ID；对象已删除时置空',
  `before_data`       JSON            DEFAULT NULL            COMMENT '变更前数据快照（JSON）；新增类操作 NULL',
  `after_data`        JSON            DEFAULT NULL            COMMENT '变更后数据快照（JSON）；删除类操作 NULL',
  `operated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间，写入后不随更新变化',
  `remark`            VARCHAR(255)    DEFAULT NULL            COMMENT '补充说明（如取消原因摘要）',
  PRIMARY KEY (`id`),
  KEY `idx_log_operator` (`operator_id`),
  KEY `idx_log_time` (`operated_at`),
  KEY `idx_log_type` (`operation_type`),
  KEY `idx_log_target` (`target_type`, `target_id`),
  CONSTRAINT `ck_log_operation_type` CHECK (`operation_type` IN (
    'USER_CREATE', 'USER_DISABLE', 'USER_ENABLE', 'ROLE_CHANGE',
    'ROOM_CREATE', 'ROOM_UPDATE', 'ROOM_DELETE', 'ROOM_STATUS',
    'BOOKING_FORCE_CANCEL'
  )),
  CONSTRAINT `ck_log_target_type` CHECK (`target_type` IN ('USER', 'ROOM', 'BOOKING')),
  CONSTRAINT `fk_log_operator` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '管理员敏感操作日志表（NF-14）；普通用户不可查看，业务界面不可删除';


-- =============================================================================
-- 结构自检：确认 4 张表就位（不打印任何口令或摘要）
-- =============================================================================
SELECT TABLE_NAME AS `已建表`, TABLE_COMMENT AS `说明`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meeting_room'
ORDER BY TABLE_NAME;

SELECT '下一步' AS `提示`, '执行 database/02_seed.sql 灌入演示数据；或直接用自包含版 docs/week07-08/schema.sql' AS `说明`;
