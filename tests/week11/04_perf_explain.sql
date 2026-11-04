-- =============================================================================
-- 第 11 周 性能瓶颈定位：1000 条数据下的 EXPLAIN 与查询改写验证（虞业宸）
--
-- ★ 为什么用独立库 meeting_room_w11perf：
--   NF-15 要求测试数据隔离。灌 1000 条进演示库会毁掉第 10 周演示数据，
--   而且会真的消耗自增 id、污染编号序列。这里用 CREATE TABLE ... LIKE 复制表结构
--   （不复制外键，灌数不会被 FK 挡住），灌完只影响这个库。
--
-- ★ 被测对象（任务书点名两个接口 + 我怀疑的一处写操作）：
--   1. GET /api/bookings/mine   → selectMine
--   2. GET /api/rooms           → selectOccupiedSlots
--   3. mine() 里每次都执行的 correctOverduePending（UPDATE，潜在全表扫描）
--
-- 执行： mysql -uroot -P3306 -h127.0.0.1 --table < tests/week11/04_perf_explain.sql
-- =============================================================================

DROP DATABASE IF EXISTS meeting_room_w11perf;
CREATE DATABASE meeting_room_w11perf DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE meeting_room_w11perf;

CREATE TABLE reservation LIKE meeting_room.reservation;
CREATE TABLE meeting_room LIKE meeting_room.meeting_room;
INSERT INTO meeting_room SELECT * FROM meeting_room.meeting_room;

-- 灌 1000 条（任务书硬指标：不许减少数据量）
INSERT INTO reservation
    (reservation_no, user_id, room_id, reserve_date, start_time, end_time,
     topic, attendee_count, contact, status)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT CONCAT('MR2027', LPAD(n, 7, '0')),
       2,
       1 + (n % 3),
       DATE_ADD('2026-11-01', INTERVAL (n % 300) DAY),
       TIMESTAMP(DATE_ADD('2026-11-01', INTERVAL (n % 300) DAY), SEC_TO_TIME((n % 12) * 3600)),
       TIMESTAMP(DATE_ADD('2026-11-01', INTERVAL (n % 300) DAY), SEC_TO_TIME(((n % 12) + 1) * 3600)),
       CONCAT('PERF-', n), 4, '压测',
       IF(n % 7 = 0, 'PENDING', 'FINISHED')
FROM seq;

SELECT COUNT(*) AS `reservation_rows_1000` FROM reservation;

-- -----------------------------------------------------------------------------
-- ① selectMine：按 user_id 查、按 start_time 倒序
--   期望命中 idx_reservation_user_date(user_id, reserve_date)
-- -----------------------------------------------------------------------------
EXPLAIN
SELECT r.id, r.reservation_no, r.room_id, m.name, r.reserve_date,
       r.start_time, r.end_time, r.topic, r.attendee_count, r.contact, r.status
FROM reservation r
         JOIN meeting_room m ON m.id = r.room_id
WHERE r.user_id = 2
ORDER BY r.start_time DESC;

-- -----------------------------------------------------------------------------
-- ② selectOccupiedSlots：按 room_id 集合 + 日期
--   期望命中 idx_reservation_room_time(room_id, reserve_date, start_time, end_time)
-- -----------------------------------------------------------------------------
EXPLAIN
SELECT room_id, start_time, end_time
FROM reservation
WHERE room_id IN (1, 2, 3)
  AND reserve_date = '2026-11-15'
  AND status <> 'CANCELLED';

-- -----------------------------------------------------------------------------
-- ③ correctOverduePending（现状）：status + end_time，两个列都不是任何索引的前导列
--   期望：type=ALL 全表扫描 —— 这就是瓶颈
-- -----------------------------------------------------------------------------
EXPLAIN UPDATE reservation
SET status = 'FINISHED'
WHERE status = 'PENDING'
  AND end_time < NOW();

-- -----------------------------------------------------------------------------
-- ④ correctOverduePending（改写后）：补一个 reserve_date <= CURDATE()
--   逻辑等价性：end_time < NOW() ⇒ reserve_date <= 今天（跨天预约在建单时已被拒绝）
--   这样能吃到 idx_reservation_date_status(reserve_date, status) 的范围扫描
-- -----------------------------------------------------------------------------
EXPLAIN UPDATE reservation
SET status = 'FINISHED'
WHERE status = 'PENDING'
  AND end_time < NOW()
  AND reserve_date <= CURDATE();

-- -----------------------------------------------------------------------------
-- ⑤ 结果等价性核对：两种写法命中的行数必须一致（否则不能算"只改写法不改行为"）
-- -----------------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM reservation WHERE status = 'PENDING' AND end_time < NOW()) AS `orig_matched`,
    (SELECT COUNT(*) FROM reservation
     WHERE status = 'PENDING' AND end_time < NOW() AND reserve_date <= CURDATE())    AS `optimized_matched`;
