-- =============================================================================
-- 种子数据校验脚本（第 6 周工程骨架）
-- 用途：灌完 database/02_seed.sql 后跑一遍，确认三张表的数据与设计一致。
-- 执行：
--   mysql -u root -p --default-character-set=utf8mb4 meeting_room < tests/sql/01_verify_seed.sql
-- 每段查询上方注释写明"期望结果"，人工比对或截图留证。
-- =============================================================================

USE `meeting_room`;

-- 【1】三张表是否都建好？期望：3 行（meeting_room / reservation / user）
SELECT TABLE_NAME AS `表名`, TABLE_ROWS AS `估算行数`, TABLE_COMMENT AS `说明`
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'meeting_room'
ORDER BY TABLE_NAME;

-- 【2】默认账号是否就位？期望：2 行（student01=USER、admin01=ADMIN），status 均为 1
SELECT `id`, `username`, `real_name` AS `姓名`, `role` AS `角色`, `status` AS `状态`,
       CHAR_LENGTH(`password`) AS `密码长度`   -- 期望 60（BCrypt 密文长度）
FROM `user`
ORDER BY `id`;

-- 【3】会议室是否 ≥3 间？期望：4 行，其中「主楼 A105」status=0（停用）
SELECT `id`, `name` AS `名称`, `location` AS `位置`, `capacity` AS `容量`, `status` AS `状态`
FROM `meeting_room`
ORDER BY `id`;

-- 【4】前台查询口径（FR-06）预演：期望 3 行，**不应**出现「主楼 A105」
SELECT `id`, `name` AS `名称`, `capacity` AS `容量`
FROM `meeting_room`
WHERE `status` = 1
ORDER BY `capacity`;

-- 【5】预约记录状态分布，期望：PENDING=5、CANCELLED=1、FINISHED=1（共 7 行）
SELECT `status` AS `状态`, COUNT(*) AS `条数`
FROM `reservation`
GROUP BY `status`
ORDER BY `status`;

-- 【6】半开区间教学样例（**本脚本最重要的校验**）
--     期望：主楼 A301 / 2026-10-14 有 3 条：09:00-10:00、10:00-11:00、14:00-15:30
--     其中前两条首尾相接（10:00:00 == 10:00:00），按 [start, end) 规则【不算冲突】。
SELECT r.`id`, m.`name` AS `会议室`, r.`reserve_date` AS `日期`,
       r.`start_time` AS `开始`, r.`end_time` AS `结束`,
       r.`status` AS `状态`, r.`purpose` AS `用途`
FROM `reservation` r
         JOIN `meeting_room` m ON m.`id` = r.`room_id`
WHERE m.`name` = '主楼 A301' AND r.`reserve_date` = '2026-10-14'
  AND r.`status` <> 'CANCELLED'
ORDER BY r.`start_time`;

-- 【7】冲突判定 SQL 演示（供第 9～10 周实现 FR-09 时直接复用）
--     半开区间判定：start_new < end_old AND end_new > start_old
--     当前参数：主楼 A301 / 2026-10-14 / 11:00-12:00
--       期望结果：0 行（11:00 与已有预约的 end_time 11:00 相接，相邻【不算冲突】）
--       把 @new_start 改成 10:30:00、@new_end 改成 11:30:00 再执行，
--       期望结果：1 行（命中 id=2 的 10:00-11:00，因为 10:30 < 11:00 且 11:30 > 10:00 成立）
SET @room_id   = 1;
SET @new_date  = '2026-10-14';
SET @new_start = '11:00:00';
SET @new_end   = '12:00:00';

SELECT r.`id`, r.`start_time` AS `已有开始`, r.`end_time` AS `已有结束`, r.`status` AS `状态`
FROM `reservation` r
WHERE r.`room_id` = @room_id
  AND r.`reserve_date` = @new_date
  AND r.`status` <> 'CANCELLED'                       -- 已取消的预约不占用时段
  AND CAST(@new_start AS TIME) < r.`end_time`         -- 半开区间：左端严格小于右端
  AND CAST(@new_end   AS TIME) > r.`start_time`       -- 半开区间：右端严格大于左端
ORDER BY r.`start_time`;

-- 【8】外键与索引是否生效，期望：3 条索引（idx_res_room_date / idx_res_room_date_time / uk_user_username 等）
SELECT TABLE_NAME AS `表`, INDEX_NAME AS `索引`, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS `列`
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'meeting_room'
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;
