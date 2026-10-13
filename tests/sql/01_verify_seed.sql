-- =============================================================================
-- 种子数据校验脚本（第 6 周工程骨架）
-- 用途：灌完 database/02_seed.sql 后跑一遍，确认三张表的数据与设计一致。
-- 执行：
--   mysql -u root -p --default-character-set=utf8mb4 meeting_room < tests/sql/01_verify_seed.sql
-- 每段查询上方注释写明"期望结果"，人工比对或截图留证。
--
-- 注意：种子数据中的预约日期用 CURDATE() 动态生成，因此本脚本**须在灌数据当天执行**，
--       否则第 6、7 段的"今日占用"会查不到行。
-- =============================================================================

USE `meeting_room`;

-- 【1】三张表是否都建好？期望：3 行（meeting_room / reservation / user）
SELECT TABLE_NAME AS `表名`, TABLE_ROWS AS `估算行数`, TABLE_COMMENT AS `说明`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meeting_room'
ORDER BY TABLE_NAME;

-- 【2】默认账号是否就位？期望：3 行
--     admin=ADMIN、user1=USER、user2=USER，status 均为 ACTIVE；
--     密码长度均为 60 且以 $2b$ 开头（BCrypt 摘要），**不应出现明文 123456**
SELECT `id`, `username` AS `账号`, `real_name` AS `姓名`, `role` AS `角色`, `status` AS `状态`,
       CHAR_LENGTH(`password`) AS `密码长度`,          -- 期望 60
       LEFT(`password`, 4) AS `摘要前缀`,              -- 期望 $2b$
       CASE WHEN `password` = '123456' THEN '★明文，未摘要！' ELSE 'ok' END AS `明文检查`
FROM `user`
ORDER BY `id`;

-- 【3】会议室是否 ≥3 间？期望：4 行，其中「学生活动中心C102洽谈室」status=DISABLED（停用）
SELECT `id`, `name` AS `名称`, `location` AS `位置`, `capacity` AS `容量`,
       `equipment` AS `设备`, `status` AS `状态`
FROM `meeting_room`
ORDER BY `id`;

-- 【4】前台查询口径（FR-06）预演：期望 3 行，**不应**出现「学生活动中心C102洽谈室」
SELECT `id`, `name` AS `名称`, `capacity` AS `容量`
FROM `meeting_room`
WHERE `status` = 'ENABLED'
ORDER BY `capacity`;

-- 【5】预约记录状态分布，期望：PENDING=5（共 5 行）
SELECT `status` AS `状态`, COUNT(*) AS `条数`
FROM `reservation`
GROUP BY `status`
ORDER BY `status`;

-- 【6】半开区间教学样例（**本脚本最重要的校验**）
--     期望：逸夫楼A101会议室 / 今天 有 3 条：09:00-10:00、10:00-11:00、14:00-16:00
--     其中前两条首尾相接（10:00:00 == 10:00:00），按 [start, end) 规则【不算冲突】。
SELECT r.`id`, m.`name` AS `会议室`, r.`reservation_no` AS `预约编号`,
       r.`reserve_date` AS `日期`,
       DATE_FORMAT(r.`start_time`, '%H:%i') AS `开始`,
       DATE_FORMAT(r.`end_time`, '%H:%i') AS `结束`,
       r.`status` AS `状态`, r.`purpose` AS `用途`
FROM `reservation` r
         JOIN `meeting_room` m ON m.`id` = r.`room_id`
WHERE m.`name` = '逸夫楼A101会议室' AND r.`reserve_date` = CURDATE()
  AND r.`status` <> 'CANCELLED'
ORDER BY r.`start_time`;

-- 【7】冲突判定 SQL 演示（供第 9～10 周实现 FR-09 时直接复用）
--     半开区间判定：start_new < end_old AND end_new > start_old
--     当前参数：逸夫楼A101会议室 / 今天 / 11:00-12:00
--       期望结果：0 行（11:00 与已有预约的 end_time 11:00 相接，相邻【不算冲突】）
--       把 @new_start 改成 '10:30:00'、@new_end 改成 '11:30:00' 再执行，
--       期望结果：1 行（命中 10:00-11:00 那条，因为 10:30 < 11:00 且 11:30 > 10:00 成立）
SET @room_id   = 1;
SET @new_date  = CURDATE();
SET @new_start = TIMESTAMP(@new_date, '11:00:00');
SET @new_end   = TIMESTAMP(@new_date, '12:00:00');

SELECT r.`id`, r.`reservation_no` AS `已有预约`,
       r.`start_time` AS `已有开始`, r.`end_time` AS `已有结束`, r.`status` AS `状态`
FROM `reservation` r
WHERE r.`room_id` = @room_id
  AND r.`reserve_date` = @new_date
  AND r.`status` <> 'CANCELLED'        -- 已取消的预约不占用时段
  AND @new_start < r.`end_time`        -- 半开区间：左端严格小于右端
  AND @new_end   > r.`start_time`      -- 半开区间：右端严格大于左端
ORDER BY r.`start_time`;

-- 【8】索引是否生效
--     期望：user 上有 uk_user_username / idx_user_role；
--           meeting_room 上有 uk_room_name / idx_room_status；
--           reservation 上有 uk_reservation_no / idx_reservation_room_time /
--           idx_reservation_user / idx_reservation_date_status
SELECT TABLE_NAME AS `表`, INDEX_NAME AS `索引`, NON_UNIQUE AS `非唯一`,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS `列`
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'meeting_room'
GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;

-- 【9】CHECK 约束是否生效（MySQL 8.0.16+ 才真正执行）
--     期望：8 条 —— user 2 条（ck_user_role / ck_user_status）、
--                     meeting_room 2 条（ck_room_capacity / ck_room_status）、
--                     reservation 4 条（ck_reservation_time_order / _same_day / _max_8h / _status）
--     若这一节少于 8 条，多半是 MySQL 版本低于 8.0.16，CHECK 被解析但不执行。
SELECT CONSTRAINT_NAME AS `约束`, TABLE_NAME AS `表`
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'meeting_room' AND CONSTRAINT_TYPE = 'CHECK'
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
