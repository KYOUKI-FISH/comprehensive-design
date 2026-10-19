-- =============================================================================
-- 智能会议室管理系统 V1.0 —— 种子数据脚本（第 7—8 周 · 数据库定稿 / 拆分版 · 数据部分）
-- 文件    : database/02_seed.sql
-- 负责人  : 虞业宸（2025091202009，Git: yycyuyechen）
-- 前置条件: 已执行 database/01_schema.sql 建库建表
-- 执行方式: mysql -u root -p --default-character-set=utf8mb4 < database/02_seed.sql
--
-- ★ 本文件是"数据"拆分版：只清空并重灌演示数据，不动表结构。
--   第 7—8 周的正式交付物是自包含版 docs/week07-08/schema.sql
--   （建库 + 建表 + 约束 + 索引 + 初始化数据在同一个文件里，供课堂"干净库一次跑通"演示）。
--   两份内容同源同表，改数据时须同步改。
--
-- ⚠️ 本脚本会清空 4 张表的全部数据后重灌，仅用于初始化 / 重置。
-- =============================================================================

SET NAMES utf8mb4;
USE `meeting_room`;

-- 按外键依赖倒序清空（先关外键检查，避免顺序约束），并重置自增
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `operation_log`;
TRUNCATE TABLE `reservation`;
TRUNCATE TABLE `meeting_room`;
TRUNCATE TABLE `user`;
SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 1. 用户（1 管理员 + 2 普通用户）
--    口令口径：password 只存 BCrypt 摘要（cost=10，各账号独立盐值），本文件不出现明文。
--    演示账号 admin / user1 / user2 的初始口令只写在根 README.md。
--    多给 1 个 user2：任务书口径是下限（1 管理员 + 1 普通用户），
--    第 3 个账号用于让冲突组两条预约分属不同用户，从而能验证 FR-11
--    "我的预约只显示本人记录"（同一账号的两条预约看不出归属隔离）。
-- -----------------------------------------------------------------------------
INSERT INTO `user` (`id`, `username`, `password`, `real_name`, `role`, `phone`, `status`) VALUES
  (1, 'admin', '$2a$10$6l2Y.jlb5dFfu5dxJ3MMxehZ.ZdS0zPkjul8hDtf2/jvIrxEMoqEe', '系统管理员', 'ADMIN', '13800000001', 'ACTIVE'),
  (2, 'user1', '$2a$10$5g8hM1oUiZoB9HI1kaYjIulDurZJLxQGYsEW9j/wOE2JYi1HrAbYW', '张三',       'USER',  '13800000002', 'ACTIVE'),
  (3, 'user2', '$2a$10$g4wqnD6pV68YvQJUHUW3/OY81Ic.SqsEp/qN7W1t5cr35OJuBXkf2', '李四',       'USER',  '13800000003', 'ACTIVE');

-- -----------------------------------------------------------------------------
-- 2. 会议室（3 间：2 间 ENABLED + 1 间 DISABLED）
--    DISABLED 那间用于验证 FR-06：查询结果标"不可用"，且不能作为新预约选项。
-- -----------------------------------------------------------------------------
INSERT INTO `meeting_room` (`id`, `name`, `building`, `room_no`, `capacity`, `equipment`, `purpose`, `status`, `remark`) VALUES
  (1, '逸夫楼A101会议室', '逸夫楼A座', '101', 20, '投影仪、白板、视频会议终端', '项目组例会、需求评审', 'ENABLED',  '本组演示主用会议室'),
  (2, '逸夫楼A202会议室', '逸夫楼A座', '202', 12, '投影仪、白板',               '小组讨论、技术评审',   'ENABLED',  '容量较小，适合小组讨论'),
  (3, '图书馆B301报告厅', '图书馆B座',  '301', 40, '投影仪、音响、话筒',         '培训、讲座',           'DISABLED', '维修中，已停用；用于验证 FR-06 停用过滤');

-- -----------------------------------------------------------------------------
-- 3. 预约（7 条）
--    a) A101（room_id=1）2026-11-02 09:00-11:00 与 10:00-12:00 → 时间重叠，**冲突组**（FR-09）
--    b) A202（room_id=2）2026-11-02 09:00-10:00 与 10:00-11:00 → **首尾相接，不算冲突**（FR-09）
--    c) A101 2026-11-02 14:00-15:00 → status=3 已取消，时段已释放（NF-08）
--    d) A202 2026-10-12 09:00-10:00 → status=2 已结束（覆盖第三种状态）
--    e) 图书馆 B301 2026-11-02 09:00-10:00 → 挂在已停用会议室上的历史预约（FR-04 停用保留已有预约）
--    日期用固定值 2026-11-02 保证可复现；演示时把预约日期选 2026-11-02 即可复现冲突。
-- -----------------------------------------------------------------------------
INSERT INTO `reservation`
  (`booking_no`, `user_id`, `room_id`, `booking_date`, `start_time`, `end_time`,
   `topic`, `attendee_count`, `contact`, `purpose`, `status`, `cancel_reason`) VALUES
  ('BK202611020001', 2, 1, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 11:00:00',
   '项目组周例会', 8, '张三', '同步本周进度', 0, NULL),
  ('BK202611020002', 3, 1, '2026-11-02', '2026-11-02 10:00:00', '2026-11-02 12:00:00',
   '需求评审会', 10, '李四', '评审 V1.1 需求清单', 0, NULL),
  ('BK202611020003', 2, 2, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 10:00:00',
   '技术方案讨论', 6, '张三', '接口边界讨论', 0, NULL),
  ('BK202611020004', 3, 2, '2026-11-02', '2026-11-02 10:00:00', '2026-11-02 11:00:00',
   '数据库设计评审', 5, '李四', '表结构与索引评审', 0, NULL),
  ('BK202611020005', 2, 1, '2026-11-02', '2026-11-02 14:00:00', '2026-11-02 15:00:00',
   '临时沟通会', 3, '张三', '因主讲人有事取消', 3, '主讲人临时出差，管理员强制取消'),
  ('BK202610120006', 2, 2, '2026-10-12', '2026-10-12 09:00:00', '2026-10-12 10:00:00',
   '第 6 周阶段自查', 5, '张三', '第 6 周收尾检查', 2, NULL),
  ('BK202611020007', 3, 3, '2026-11-02', '2026-11-02 09:00:00', '2026-11-02 10:00:00',
   '新技术培训', 25, '李四', '停用前已订下，保留记录', 0, NULL);

-- -----------------------------------------------------------------------------
-- 4. 操作日志示例（NF-14：管理员敏感操作留痕）
-- -----------------------------------------------------------------------------
INSERT INTO `operation_log`
  (`operator_id`, `operator_username`, `operation_type`, `target_type`, `target_id`,
   `before_data`, `after_data`, `operated_at`, `remark`) VALUES
  (1, 'admin', 'BOOKING_FORCE_CANCEL', 'BOOKING', 5,
   JSON_OBJECT('status', 0, 'bookingNo', 'BK202611020005'),
   JSON_OBJECT('status', 3, 'cancelReason', '主讲人临时出差，管理员强制取消'),
   '2026-11-02 09:30:00', '演示数据：管理员强制取消异常预约');


-- =============================================================================
-- 灌入结果自检（不打印任何口令或摘要，可安全投屏）
-- =============================================================================
SELECT '① 各表记录数' AS `校验项`, '' AS `结果`;
SELECT 'user'          AS `表`, COUNT(*) AS `条数` FROM `user`
UNION ALL SELECT 'meeting_room',  COUNT(*) FROM `meeting_room`
UNION ALL SELECT 'reservation',   COUNT(*) FROM `reservation`
UNION ALL SELECT 'operation_log', COUNT(*) FROM `operation_log`;

SELECT '② 默认账号一览（不显示口令）' AS `校验项`, '' AS `结果`;
SELECT `username` AS `账号`, `real_name` AS `姓名`, `role` AS `角色`, `status` AS `状态`
FROM `user` ORDER BY `id`;

SELECT '③ 会议室一览' AS `校验项`, '' AS `结果`;
SELECT `id`, `name` AS `名称`, `building` AS `楼栋`, `room_no` AS `房间号`,
       `capacity` AS `容量`, `status` AS `状态`
FROM `meeting_room` ORDER BY `id`;

SELECT '④ 预约状态分布' AS `校验项`, '' AS `结果`;
SELECT `status` AS `状态值`,
       CASE `status` WHEN 0 THEN '待开始' WHEN 1 THEN '进行中'
                     WHEN 2 THEN '已结束' WHEN 3 THEN '已取消' END AS `含义`,
       COUNT(*) AS `条数`
FROM `reservation` GROUP BY `status` ORDER BY `status`;

SELECT '⑤ 冲突组实证：A101 在 2026-11-02' AS `校验项`, '' AS `结果`;
SELECT '新预约 10:30-11:30（与 09:00-11:00 重叠，应判冲突）' AS `场景`, COUNT(*) AS `命中冲突条数`
FROM `reservation`
WHERE `room_id` = 1 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 11:30:00' AND `end_time` > '2026-11-02 10:30:00'
UNION ALL
SELECT '新预约 14:00-15:00（原预约已取消 status=3，应判不冲突）', COUNT(*)
FROM `reservation`
WHERE `room_id` = 1 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 15:00:00' AND `end_time` > '2026-11-02 14:00:00';

SELECT '⑥ 首尾相接实证：A202 在 2026-11-02（现有 09:00-10:00 与 10:00-11:00）' AS `校验项`, '' AS `结果`;
SELECT '新预约 11:00-12:00（接尾部，应判不冲突）' AS `场景`, COUNT(*) AS `命中冲突条数`
FROM `reservation`
WHERE `room_id` = 2 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 12:00:00' AND `end_time` > '2026-11-02 11:00:00'
UNION ALL
SELECT '新预约 08:00-09:00（接头，应判不冲突）', COUNT(*)
FROM `reservation`
WHERE `room_id` = 2 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 09:00:00' AND `end_time` > '2026-11-02 08:00:00'
UNION ALL
SELECT '新预约 09:30-10:30（真重叠，应判冲突）', COUNT(*)
FROM `reservation`
WHERE `room_id` = 2 AND `booking_date` = '2026-11-02' AND `status` IN (0, 1)
  AND `start_time` < '2026-11-02 10:30:00' AND `end_time` > '2026-11-02 09:30:00';
