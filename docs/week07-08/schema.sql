-- =============================================================================
-- 智能会议室管理系统 V1.0 —— 建表脚本（第 7—8 周交付版 · 自包含）
-- 文件    : docs/week07-08/schema.sql
-- 负责人  : 虞业宸（2025091202009，Git: yycyuyechen）
-- 对应文档: docs/week07-08/02_数据库与接口设计.md（表结构、状态取值、索引与约束逐条对应）
-- 对应需求: FR-01～FR-20 / NF-03、NF-05、NF-07、NF-08、NF-13、NF-14
--
-- 【本脚本是自包含的】建库 + 建表 + 约束 + 索引 + 初始化数据全在这一个文件里，
--   在干净数据库上一条命令按顺序执行即可，不依赖 database/ 目录下的拆分版。
--   干净库验证命令（README 同步）：
--       mysql -u root -p --default-character-set=utf8mb4 < docs/week07-08/schema.sql
--   database/01_schema.sql ＋ 02_seed.sql 是同一设计的拆分版，内容同源，
--   供日常开发单独重置结构 / 单独重灌数据使用。
--
-- 【环境要求】MySQL 8.0.16 及以上。CHECK 约束自 8.0.16 起才被真正执行，
--   低于该版本脚本仍能建表，但 CHECK 会被静默忽略（FR-08 的时间边界就失去数据库兜底）。
--   字符集统一 utf8mb4（中文表名注释、会议主题、姓名均按 UTF-8 存储）。
--
-- 【密码口径】user.password 只存 BCrypt 摘要（cost=10，固定 60 字符），
--   本脚本中不出现任何明文口令，也不在收尾输出里打印摘要。
--   演示账号 admin / user1 / user2 的初始口令只写在根 README.md，本脚本不出现明文。
--   后端校验必须用 BCryptPasswordEncoder.matches()，禁止字符串相等比较。
--
-- ⚠️ 本脚本会 DROP 并重建 4 张表，会清空这 4 张表的全部数据，仅用于初始化 / 重置。
--    不要在有真实数据的环境执行。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 【核心业务规则 · 半开区间 [start, end)】（与需求清单 V1.1 FR-09 同源）
--   一条预约占用的时间区间记作 [start_time, end_time)：含开始时刻、不含结束时刻。
--   冲突判定：新预约 [s_new, e_new) 与已有有效预约 [s_old, e_old)
--             当 s_new < e_old 且 e_new > s_old 时，判为冲突。
--   ★ 首尾相接不算冲突：09:00-10:00 与 10:00-11:00 可以同时存在。
--   支撑查询（第 9 周实现，本脚本只提供索引与数据）：
--       SELECT 1 FROM reservation
--        WHERE room_id = ? AND booking_date = ?
--          AND status IN (0, 1)                              -- 0 待开始 / 1 进行中 才算占位
--          AND start_time < ?   -- 新预约的 end_time
--          AND end_time   > ?   -- 新预约的 start_time
--        LIMIT 1;
--   索引 idx_reservation_room_time(room_id, booking_date, start_time, end_time)
--   正好覆盖该查询的等值列（room_id、booking_date）与范围列（start_time、end_time）。
--   并发下单靠"事务 + 命中级行锁"兜底（NF-07），不靠应用层先查后插。
-- -----------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS `meeting_room`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `meeting_room`;

-- 按外键依赖倒序删除，保证脚本在已有旧结构的库上也能重复执行：
-- operation_log → reservation → meeting_room → user
DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `meeting_room`;
DROP TABLE IF EXISTS `user`;


-- =============================================================================
-- 表 1／4：user　用户表（普通用户 + 管理员）
-- 需求依据：FR-01 用户登录、FR-02 角色区分与权限控制、FR-14 管理用户账号、NF-05 密码不得明文
-- 设计说明：本组只有 USER／ADMIN 两种固定角色，需求清单 FR-01～FR-20 中无"角色管理"功能，
--           故 role 以枚举列承载，**不单独建 role 表**，也不建 user_role 关联表。
--           实体关系：user 1 : N reservation（见 reservation.user_id 外键）。
-- =============================================================================
CREATE TABLE `user` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键，自增',
  `username`   VARCHAR(50)     NOT NULL                COMMENT '登录账号，全局唯一（FR-01）；管理员建号时校验重复（FR-14）',
  `password`   VARCHAR(100)    NOT NULL                COMMENT '登录密码的 BCrypt 摘要（cost=10，固定 60 字符）。禁止存明文（NF-05）；校验用 BCryptPasswordEncoder.matches()',
  `real_name`  VARCHAR(50)     NOT NULL                COMMENT '姓名，用于页面展示与操作人留痕',
  `role`       VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色枚举：USER=普通用户 / ADMIN=管理员（FR-02）。不建 role 表，需求清单无角色管理功能',
  `phone`      VARCHAR(20)     DEFAULT NULL            COMMENT '联系电话，可空；演示数据不得使用真实手机号（NF-06）',
  `status`     VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态枚举：ACTIVE=正常可登录 / DISABLED=已停用不可登录（FR-14、NF-05）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间，随任一字段变更自动刷新',
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
-- 需求依据：FR-03 会议室信息管理、FR-04 启用停用、FR-05 按条件查询、FR-06 显示可用状态
-- 设计说明：楼栋 building 与房间号 room_no 拆成两列，是为支撑 FR-03 的
--           "相同楼栋和房间号的会议室已存在时拒绝重复新增"——用 UNIQUE(building, room_no) 直接兜住。
--           实体关系：meeting_room 1 : N reservation（见 reservation.room_id 外键）。
-- =============================================================================
CREATE TABLE `meeting_room` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会议室ID，主键，自增',
  `name`       VARCHAR(100)    NOT NULL                COMMENT '会议室名称，如"逸夫楼A101会议室"，仅用于展示（唯一性由楼栋+房间号保证）',
  `building`   VARCHAR(50)     NOT NULL                COMMENT '楼栋，如"逸夫楼A座"。与 room_no 组成唯一键（FR-03）',
  `room_no`    VARCHAR(20)     NOT NULL                COMMENT '房间号，如"101"。与 building 组成唯一键（FR-03）',
  `capacity`   INT UNSIGNED    NOT NULL                COMMENT '可容纳人数，FR-05 按容量筛选的比较字段',
  `equipment`  VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '设备描述（投影仪、白板、视频会议终端等），仅作展示',
  `purpose`    VARCHAR(100)    NOT NULL DEFAULT ''     COMMENT '会议室用途说明，如"项目组例会/培训"，仅作展示与筛选提示',
  `status`     VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '会议室状态枚举：ENABLED=可用 / DISABLED=停用（FR-04）。停用后保留已有预约，但不作为新预约选项（FR-06）',
  `remark`     VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_building_no` (`building`, `room_no`),
  KEY `idx_room_status` (`status`),
  KEY `idx_room_capacity` (`capacity`),
  KEY `idx_room_building` (`building`),
  CONSTRAINT `ck_room_status`   CHECK (`status` IN ('ENABLED', 'DISABLED')),
  CONSTRAINT `ck_room_capacity` CHECK (`capacity` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '会议室表；楼栋+房间号唯一（FR-03），停用后不作为新预约选项（FR-06）';


-- =============================================================================
-- 表 3／4：reservation　预约记录表
-- 需求依据：FR-07 提交预约（必填）、FR-08 时间规则、FR-09 冲突检测、FR-10 冲突提示、
--           FR-11 我的预约、FR-12 修改、FR-13 取消、FR-15 全部预约、FR-16 处理异常、NF-08 状态流转
--
-- ★ 时间怎么存：start_time / end_time 均为 DATETIME NOT NULL，占用区间为半开区间 [start_time, end_time)。
--   booking_date 冗余存 DATE，用于按日查询、占用日历（FR-17）与按日聚合（FR-20），
--   并由 ck_reservation_date_match 保证它永远等于 DATE(start_time)，不会与 start_time 漂移。
--   为什么用 DATETIME 而不是 TIMESTAMP：DATETIME 不带时区转换，存的是"墙上时间"，
--   本系统全部在本机时区运行、不做跨时区协作，用 DATETIME 更稳妥（不受服务器 time_zone 影响）。
--
-- ★ 状态怎么存：status 用 TINYINT 而非字符串，取值口径见 NF-08（4 态）：
--   0 待开始 / 1 进行中 / 2 已结束 / 3 已取消
--   注意：任务书第三节示例给的是 3 态（0 已预约 / 1 已取消 / 2 已结束），那是格式参照；
--   本组按需求清单 V1.1 的 NF-08 落 4 态，任务书明确"图和表只是格式参照，照抄不算完成"。
--   "待开始→进行中→已结束"不在数据库里定时翻转（不引入定时任务，不属需求清单），
--   第 9 周实现口径为：读取时按当前时间推导 + 写入时落库校正（见《数据库与接口设计》§4）。
--
-- ★ 删会议室会不会影响已有预约：会，且被数据库直接拒绝——
--   fk_reservation_room 为 ON DELETE RESTRICT，会议室只要还有预约（不论状态），DELETE 就报外键错误。
--   业务层据此提示"该会议室存在有效预约，请先处理预约"（FR-03）。
--   user 同理（fk_reservation_user 为 RESTRICT），不因停用或删除用户而毁掉历史预约记录（FR-11、FR-16）。
-- =============================================================================
CREATE TABLE `reservation` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预约主键ID，自增；对外不暴露，对外用 booking_no',
  `booking_no`     VARCHAR(32)     NOT NULL                COMMENT '预约编号，全局唯一，对外展示（FR-07）。生成规则：BK + yyyyMMdd + 4位序号，如 BK202611020001，由业务层生成（第 9 周实现）',
  `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '预约人ID → user(id)，ON DELETE RESTRICT（FR-11 我的预约归属）',
  `room_id`        BIGINT UNSIGNED NOT NULL                COMMENT '会议室ID → meeting_room(id)，ON DELETE RESTRICT（FR-03 删除保护）',
  `booking_date`   DATE            NOT NULL                COMMENT '预约日期，恒等于 DATE(start_time)，由 CHECK 保证；用于按日查询与占用日历（FR-17、FR-20）',
  `start_time`     DATETIME        NOT NULL                COMMENT '开始时间，★含本时刻（半开区间左闭）；不得早于当前时间（FR-08）',
  `end_time`       DATETIME        NOT NULL                COMMENT '结束时间，★不含本时刻（半开区间右开）；须晚于 start_time（FR-08）',
  `topic`          VARCHAR(100)    NOT NULL                COMMENT '会议主题，必填（FR-07）',
  `attendee_count` INT UNSIGNED    NOT NULL                COMMENT '参会人数，必填，须大于 0（FR-07）',
  `contact`        VARCHAR(50)     NOT NULL                COMMENT '联系人，必填（FR-07）',
  `purpose`        VARCHAR(255)    DEFAULT NULL            COMMENT '用途补充说明，选填（FR-07 只要求主题/人数/联系人/起止时间必填）',
  `status`         TINYINT         NOT NULL DEFAULT 0       COMMENT '预约状态：0 待开始 / 1 进行中 / 2 已结束 / 3 已取消（NF-08，四态；不用任务书示例的三态）',
  `cancel_reason`  VARCHAR(200)    DEFAULT NULL            COMMENT '取消原因。管理员强制取消异常预约时必填（FR-16）；用户自行取消可留空',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（NF-08 状态一致性追溯）',
  `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_booking_no` (`booking_no`),
  -- 冲突检测（FR-09）与"当日占用时段 / 占用日历"（FR-17）共用这个联合索引
  KEY `idx_reservation_room_time` (`room_id`, `booking_date`, `start_time`, `end_time`),
  KEY `idx_reservation_user_date` (`user_id`, `booking_date`),
  KEY `idx_reservation_date_status` (`booking_date`, `status`),
  -- FR-08 时间边界：结束晚于开始、禁止跨天、单次不超过 8 小时
  CONSTRAINT `ck_reservation_time_order` CHECK (`end_time` > `start_time`),
  CONSTRAINT `ck_reservation_same_day`   CHECK (DATE(`start_time`) = DATE(`end_time`)),
  CONSTRAINT `ck_reservation_max_8h`     CHECK (TIMESTAMPDIFF(MINUTE, `start_time`, `end_time`) <= 480),
  -- booking_date 与 start_time 不得漂移
  CONSTRAINT `ck_reservation_date_match` CHECK (`booking_date` = DATE(`start_time`)),
  -- NF-08 四态
  CONSTRAINT `ck_reservation_status`     CHECK (`status` IN (0, 1, 2, 3)),
  -- FR-07 参会人数必须为正
  CONSTRAINT `ck_reservation_attendee`   CHECK (`attendee_count` > 0),
  -- FR-03：有预约的会议室删不掉（RESTRICT）
  CONSTRAINT `fk_reservation_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  -- FR-11／FR-16：不因删用户而毁掉预约记录（RESTRICT）
  CONSTRAINT `fk_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '预约记录表；时间按半开区间 [start_time,end_time) 判冲突（FR-09），状态四态（NF-08）';


-- =============================================================================
-- 表 4／4：operation_log　操作日志表
-- 需求依据：NF-14 可审计性（需求清单 V1.1 必做项，且有验证方式：查日志、字段完整、普通用户访问被拒）
--
-- 为什么第 8 周必须建这张表：NF-14 要求"管理员全部敏感操作：新增/停用用户、新增/删除会议室、
-- 强制取消预约、角色变更，完整记录操作人账号、操作时间、操作类型、变更前后数据"。
-- 若 ER 图与脚本漏了它，设计就与需求清单不同源——因此本组实体表由 3 张增为 4 张，
-- 对应接口清单第 13 条 GET /api/admin/logs。这是对 NF-14 的补齐，不是范围外扩张。
--
-- operator_username 冗余存一份登录账号：用户改名或被停用后，历史日志仍能显示"当时是谁操作的"。
-- 表上不设 deleted 标志、也不提供删除接口 —— 日志"不能被业务界面删除"（NF-14）。
-- =============================================================================
CREATE TABLE `operation_log` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键，自增',
  `operator_id`       BIGINT UNSIGNED NOT NULL                COMMENT '操作人ID → user(id)，ON DELETE RESTRICT，保证日志的操作人不被删除',
  `operator_username` VARCHAR(50)     NOT NULL                COMMENT '操作人账号（冗余快照）。用户改名/停用后仍可追溯当时的账号（NF-14）',
  `operation_type`    VARCHAR(50)     NOT NULL                COMMENT '操作类型：USER_CREATE 建号 / USER_DISABLE 停用 / USER_ENABLE 启用 / ROOM_CREATE 新增会议室 / ROOM_UPDATE 修改会议室 / ROOM_DELETE 删除会议室 / ROOM_STATUS 启停会议室 / BOOKING_FORCE_CANCEL 强制取消预约 / ROLE_CHANGE 角色变更（NF-14 列明的全部敏感操作）',
  `target_type`       VARCHAR(30)     NOT NULL                COMMENT '操作对象类型：USER / ROOM / BOOKING',
  `target_id`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '操作对象ID；对象已被删除时置空（如 ROOM_DELETE 成功后对象不存在）',
  `before_data`       JSON            DEFAULT NULL            COMMENT '变更前数据快照（JSON）；新增类操作为 NULL',
  `after_data`        JSON            DEFAULT NULL            COMMENT '变更后数据快照（JSON）；删除类操作为 NULL',
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
-- 初始化数据
-- 口径（《周推进计划_第7-8周》§2.2.3）：1 管理员 + 1 普通用户 + ≥3 间会议室（含 1 间 DISABLED）
--   + 1 组时间重叠的冲突预约 + 1 组首尾相接的不冲突预约 + 状态覆盖 0／3。
-- 这里给 1 管理员 + 2 普通用户：任务书口径是下限（"1 个普通用户、1 个管理员"），
--   多 1 个 user2 是为让冲突组两条预约分属不同用户，从而能验证 FR-11
--   "我的预约只显示本人记录"（同一账号的两条预约看不出归属隔离）。
--
-- ★ 口令：以下 password 均为初始口令的 BCrypt 摘要（cost=10，各自独立盐值），
--   本文件不出现明文。演示口令写在根 README.md。
-- ★ 日期：预约统一用固定日期 2026-11-02（可复现），演示时把预约日期选 2026-11-02 即可复现冲突；
--   另有 1 条 2026-10-12 的已结束预约用于覆盖 status=2。
-- =============================================================================

-- 1. 用户（1 管理员 + 2 普通用户）
INSERT INTO `user` (`id`, `username`, `password`, `real_name`, `role`, `phone`, `status`) VALUES
  (1, 'admin', '$2a$10$6l2Y.jlb5dFfu5dxJ3MMxehZ.ZdS0zPkjul8hDtf2/jvIrxEMoqEe', '系统管理员', 'ADMIN', '13800000001', 'ACTIVE'),
  (2, 'user1', '$2a$10$5g8hM1oUiZoB9HI1kaYjIulDurZJLxQGYsEW9j/wOE2JYi1HrAbYW', '张三',       'USER',  '13800000002', 'ACTIVE'),
  (3, 'user2', '$2a$10$g4wqnD6pV68YvQJUHUW3/OY81Ic.SqsEp/qN7W1t5cr35OJuBXkf2', '李四',       'USER',  '13800000003', 'ACTIVE');

-- 2. 会议室（3 间：2 间 ENABLED + 1 间 DISABLED）
--    DISABLED 那间用于验证 FR-06：查询结果里显示"不可用"，且不能作为新预约选项。
INSERT INTO `meeting_room` (`id`, `name`, `building`, `room_no`, `capacity`, `equipment`, `purpose`, `status`, `remark`) VALUES
  (1, '逸夫楼A101会议室', '逸夫楼A座', '101', 20, '投影仪、白板、视频会议终端', '项目组例会、需求评审', 'ENABLED',  '本组演示主用会议室'),
  (2, '逸夫楼A202会议室', '逸夫楼A座', '202', 12, '投影仪、白板',               '小组讨论、技术评审',   'ENABLED',  '容量较小，适合小组讨论'),
  (3, '图书馆B301报告厅', '图书馆B座',  '301', 40, '投影仪、音响、话筒',         '培训、讲座',           'DISABLED', '维修中，已停用；用于验证 FR-06 停用过滤');

-- 3. 预约（6 条）
--    a) A101（room_id=1）2026-11-02 09:00-11:00 与 10:00-12:00 → 时间重叠，**冲突组**（FR-09 判冲突）
--    b) A202（room_id=2）2026-11-02 09:00-10:00 与 10:00-11:00 → **首尾相接，不算冲突**（FR-09 关键验收点）
--    c) A101 2026-11-02 14:00-15:00 → status=3 已取消，占用已释放（NF-08 取消后时段可再预约）
--    d) A202 2026-10-12 09:00-10:00 → status=2 已结束（覆盖第三种状态）
--    e) 图书馆 B301 2026-11-02 09:00-10:00 → 挂在一间已停用的会议室上，
--       用于验证 FR-04"停用会议室已有预约保留"：查得到历史记录，但不能新增预约。
INSERT INTO `reservation`
  (`booking_no`, `user_id`, `room_id`, `booking_date`, `start_time`, `end_time`,
   `topic`, `attendee_count`, `contact`, `purpose`, `status`, `cancel_reason`) VALUES
  -- a) 冲突组：同一会议室 A101，两条时间重叠
  ('BK202611020001', 2, 1, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 11:00:00',
   '项目组周例会', 8, '张三', '同步本周进度', 0, NULL),
  ('BK202611020002', 3, 1, '2026-11-02', '2026-11-02 10:00:00', '2026-11-02 12:00:00',
   '需求评审会', 10, '李四', '评审 V1.1 需求清单', 0, NULL),
  -- b) 首尾相接组：同一会议室 A202，10:00 接 09:00，不算冲突
  ('BK202611020003', 2, 2, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 10:00:00',
   '技术方案讨论', 6, '张三', '接口边界讨论', 0, NULL),
  ('BK202611020004', 3, 2, '2026-11-02', '2026-11-02 10:00:00', '2026-11-02 11:00:00',
   '数据库设计评审', 5, '李四', '表结构与索引评审', 0, NULL),
  -- c) 已取消（status=3）：时段已释放
  ('BK202611020005', 2, 1, '2026-11-02', '2026-11-02 14:00:00', '2026-11-02 15:00:00',
   '临时沟通会', 3, '张三', '因主讲人有事取消', 3, '主讲人临时出差，管理员强制取消'),
  -- d) 已结束（status=2）
  ('BK202610120006', 2, 2, '2026-10-12', '2026-10-12 09:00:00', '2026-10-12 10:00:00',
   '第 6 周阶段自查', 5, '张三', '第 6 周收尾检查', 2, NULL),
  -- e) 挂在已停用会议室上的历史预约（FR-04：停用保留已有预约）
  ('BK202611020007', 3, 3, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 10:00:00',
   '新技术培训', 25, '李四', '停用前已订下，保留记录', 0, NULL);

-- 4. 操作日志示例（NF-14：管理员敏感操作留痕）
INSERT INTO `operation_log`
  (`operator_id`, `operator_username`, `operation_type`, `target_type`, `target_id`,
   `before_data`, `after_data`, `operated_at`, `remark`) VALUES
  (1, 'admin', 'BOOKING_FORCE_CANCEL', 'BOOKING', 5,
   JSON_OBJECT('status', 0, 'bookingNo', 'BK202611020005'),
   JSON_OBJECT('status', 3, 'cancelReason', '主讲人临时出差，管理员强制取消'),
   '2026-11-02 09:30:00', '演示数据：管理员强制取消异常预约');


-- =============================================================================
-- 收尾校验（脚本执行完自动打印，用于当场确认"一次跑通 + 数据入库"）
-- 注意：以下输出**不包含任何口令或摘要**，可安全投屏。
-- =============================================================================

SELECT '① 已建表清单' AS `校验项`, '' AS `结果`;
SELECT TABLE_NAME AS `表名`, TABLE_COMMENT AS `说明`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meeting_room'
ORDER BY TABLE_NAME;

SELECT '② 各表记录数' AS `校验项`, '' AS `结果`;
SELECT 'user'          AS `表`, COUNT(*) AS `条数` FROM `user`
UNION ALL SELECT 'meeting_room',  COUNT(*) FROM `meeting_room`
UNION ALL SELECT 'reservation',   COUNT(*) FROM `reservation`
UNION ALL SELECT 'operation_log', COUNT(*) FROM `operation_log`;

SELECT '③ 预约状态分布（应为 0/2/3 三种，NF-08 四态中的 1 未造数据）' AS `校验项`, '' AS `结果`;
SELECT `status` AS `状态值`,
       CASE `status` WHEN 0 THEN '待开始' WHEN 1 THEN '进行中'
                     WHEN 2 THEN '已结束' WHEN 3 THEN '已取消' END AS `含义`,
       COUNT(*) AS `条数`
FROM `reservation` GROUP BY `status` ORDER BY `status`;

SELECT '④ 冲突组实证：A101 在 2026-11-02' AS `校验项`, '' AS `结果`;
SELECT '新预约 10:30-11:30（与 09:00-11:00 重叠）' AS `场景`, COUNT(*) AS `命中冲突条数`
FROM `reservation`
WHERE `room_id` = 1 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 11:30:00' AND `end_time` > '2026-11-02 10:30:00'
UNION ALL
SELECT '新预约 14:00-15:00（原预约已取消 status=3，应不冲突）', COUNT(*)
FROM `reservation`
WHERE `room_id` = 1 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 15:00:00' AND `end_time` > '2026-11-02 14:00:00';

SELECT '⑤ 首尾相接实证：A202 在 2026-11-02（现有 09:00-10:00 与 10:00-11:00 两条）' AS `校验项`, '' AS `结果`;
SELECT '新预约 11:00-12:00（接 10:00-11:00 的尾巴，应判不冲突）' AS `场景`, COUNT(*) AS `命中冲突条数`
FROM `reservation`
WHERE `room_id` = 2 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 12:00:00' AND `end_time` > '2026-11-02 11:00:00'
UNION ALL
SELECT '新预约 08:00-09:00（接 09:00-10:00 的头，应判不冲突）', COUNT(*)
FROM `reservation`
WHERE `room_id` = 2 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 09:00:00' AND `end_time` > '2026-11-02 08:00:00'
UNION ALL
SELECT '新预约 09:30-10:30（真重叠，应判冲突）', COUNT(*)
FROM `reservation`
WHERE `room_id` = 2 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 10:30:00' AND `end_time` > '2026-11-02 09:30:00';
