-- =============================================================================
-- 智能会议室管理系统 V1.0 —— 种子数据（第 6 周 · 工程骨架）
-- 文件    : database/02_seed.sql
-- 负责人  : 虞业宸（2025091202009，Git: yycyuyechen）
-- 前置条件: 已执行 database/01_schema.sql 建库建表
-- 执行方式: mysql -u root -p < database/02_seed.sql
--
-- 内容口径（第 6 周推进计划 §4.3）：
--   1. 默认账号 2 组 —— 普通用户 1 组 + 管理员 1 组（见下方"默认账号"）
--   2. 会议室 3 间（启用）+ 1 间（停用，用于验证 FR-06 前台查询过滤掉停用会议室）
--   3. 密码统一以 BCrypt 摘要（cost=10，60 字符）存储，明文均为 123456；校验走 PasswordEncoder.matches()
--   4. 若干已占用时段，供查询页展示"当日占用时段"（DFD P2 的输出数据流）
--      其中 A101 的 09:00-10:00 与 10:00-11:00 刻意首尾相接，
--      用于验证"首尾相接不算冲突"（FR-09 / UC-U03 业务规则）。
--
-- ⚠️ 本脚本会先清空三张表再灌入，可重复执行；仅用于演示数据初始化 / 重置。
--    预约日期使用 CURDATE() 动态生成，因此任何一天执行都能在查询页看到"今天"的占用时段。
-- =============================================================================

USE `meeting_room`;
SET NAMES utf8mb4;

-- 按外键依赖倒序清空（保留表结构）
DELETE FROM `reservation`;
DELETE FROM `meeting_room`;
DELETE FROM `user`;
ALTER TABLE `user`         AUTO_INCREMENT = 1;
ALTER TABLE `meeting_room` AUTO_INCREMENT = 1;
ALTER TABLE `reservation`  AUTO_INCREMENT = 1;


-- -----------------------------------------------------------------------------
-- 1. 默认账号（★ README「快速启动」章节列出的默认账号就是这两组）
--    密码明文均为 123456，存的是 BCrypt 摘要（cost=10）。同一明文的密文每次生成都不同，
--    因此三行密文各不相同 —— 这是正常的，不是写错。
--    注：另补 1 个普通用户 user2，仅用于让种子预约分属不同用户、演示"我的预约"归属。
-- -----------------------------------------------------------------------------
INSERT INTO `user` (`id`, `username`, `password`, `real_name`, `role`, `phone`, `status`) VALUES
  (1, 'admin', '$2b$10$1gnhT.Gsljg61JJyMwvdtOp23nFC8Vhwxr3uWsQw9S4pQHPNa6cJu', '系统管理员', 'ADMIN', '13800000001', 'ACTIVE'),
  (2, 'user1', '$2b$10$mRQrsAwrnNDnACz7m.9ps.xIHSXPGAjMAlzkEV99LUAMnSQVYPz5u', '张三',      'USER',  '13800000002', 'ACTIVE'),
  (3, 'user2', '$2b$10$d/uqNvF8r6jpUBuiOPod/enxpsEy3WsQqwzDM4ORL4G/fsj1TIy2u', '李四',      'USER',  '13800000003', 'ACTIVE');


-- -----------------------------------------------------------------------------
-- 2. 会议室（3 间启用 + 1 间停用）
-- -----------------------------------------------------------------------------
INSERT INTO `meeting_room` (`id`, `name`, `location`, `capacity`, `equipment`, `status`, `remark`) VALUES
  (1, '逸夫楼A101会议室', '逸夫楼A座1层', 20, '投影仪、白板、视频会议终端', 'ENABLED',  '常用于项目组例会'),
  (2, '逸夫楼A205研讨室', '逸夫楼A座2层',  8, '白板、电视屏',               'ENABLED',  '小规模讨论用'),
  (3, '图书馆B301培训室', '图书馆B座3层',  50, '投影仪、音响、录播设备',     'ENABLED',  '大容量，可培训'),
  (4, '学生活动中心C102洽谈室', '学生活动中心C座1层', 6, '电视屏、茶水', 'DISABLED', '装修中，已停用 —— 用于验证 FR-06 前台查询过滤');


-- -----------------------------------------------------------------------------
-- 3. 已占用时段（今日 3 条 + 明日 2 条，状态均为待开始 PENDING）
--    预约编号规则：MR + yyyyMMdd + 三位流水号
-- -----------------------------------------------------------------------------
INSERT INTO `reservation`
  (`reservation_no`, `room_id`, `user_id`, `reserve_date`, `start_time`, `end_time`, `purpose`, `status`) VALUES
  -- A101 今日 09:00-10:00 与 10:00-11:00 首尾相接：两者不冲突，可同时存在
  (CONCAT('MR', DATE_FORMAT(CURDATE(), '%Y%m%d'), '001'), 1, 2, CURDATE(),
   TIMESTAMP(CURDATE(), '09:00:00'), TIMESTAMP(CURDATE(), '10:00:00'), '项目组周例会', 'PENDING'),
  (CONCAT('MR', DATE_FORMAT(CURDATE(), '%Y%m%d'), '002'), 1, 3, CURDATE(),
   TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '11:00:00'), '需求评审会',   'PENDING'),
  -- A101 今日 14:00-16:00：验证"冲突查询能命中"
  (CONCAT('MR', DATE_FORMAT(CURDATE(), '%Y%m%d'), '003'), 1, 2, CURDATE(),
   TIMESTAMP(CURDATE(), '14:00:00'), TIMESTAMP(CURDATE(), '16:00:00'), '技术方案讨论', 'PENDING'),
  -- B301 今日 16:00-18:00
  (CONCAT('MR', DATE_FORMAT(CURDATE(), '%Y%m%d'), '004'), 3, 2, CURDATE(),
   TIMESTAMP(CURDATE(), '16:00:00'), TIMESTAMP(CURDATE(), '18:00:00'), '新技术培训',   'PENDING'),
  -- A205 明日 13:30-15:00（跨到"明天"，用于验证按日期查询）
  (CONCAT('MR', DATE_FORMAT(CURDATE() + INTERVAL 1 DAY, '%Y%m%d'), '001'), 2, 3, CURDATE() + INTERVAL 1 DAY,
   TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '13:30:00'), TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '15:00:00'),
   '小组讨论', 'PENDING');


-- -----------------------------------------------------------------------------
-- 4. 自带验证：核对灌入结果
-- -----------------------------------------------------------------------------
SELECT '用户'   AS `表`, COUNT(*) AS `条数` FROM `user`
UNION ALL SELECT '会议室', COUNT(*) FROM `meeting_room`
UNION ALL SELECT '预约',   COUNT(*) FROM `reservation`;

-- 默认账号一览（README「默认账号」一节的来源）
-- 密码列不打印密文本身：只校验"是否确为 BCrypt 摘要"，避免把哈希散到日志与截图里。
-- 明文一律是 123456。若这一列出现「★非 BCrypt 摘要」，说明种子脚本被改回了明文。
SELECT `username` AS `账号`,
       CASE WHEN `password` LIKE '$2%' AND CHAR_LENGTH(`password`) = 60
            THEN 'BCrypt 摘要 ✓' ELSE '★非 BCrypt 摘要' END AS `密码格式`,
       `real_name` AS `姓名`, `role` AS `角色`, `status` AS `状态`
FROM `user` ORDER BY `id`;

-- 今日占用时段一览（查询页应展示的内容）
SELECT r.`reservation_no` AS `预约编号`, m.`name` AS `会议室`,
       DATE_FORMAT(r.`start_time`, '%H:%i') AS `开始`, DATE_FORMAT(r.`end_time`, '%H:%i') AS `结束`,
       r.`purpose` AS `用途`, r.`status` AS `状态`
FROM `reservation` r
JOIN `meeting_room` m ON m.`id` = r.`room_id`
WHERE r.`reserve_date` = CURDATE()
ORDER BY r.`room_id`, r.`start_time`;
