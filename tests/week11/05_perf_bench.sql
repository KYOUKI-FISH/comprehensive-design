-- =============================================================================
-- 第 11 周 性能瓶颈量化：correctOverduePending 两种写法的扫描代价对比（虞业宸）
--
-- 为什么用 SET status = status 而不是真更新：
--   基线要"可重复"。真更新会把 3 条 PENDING 改成 FINISHED，第二次跑就一条都不匹配，
--   测出来的是"没事可干"的时间，不是扫描代价。SET status = status 保留扫描与回写路径，
--   但不改数据 —— 测的是【扫多少行】这件事本身。
--
-- 执行： mysql -uroot -P3306 -h127.0.0.1 --table < tests/week11/05_perf_bench.sql
-- =============================================================================
USE meeting_room_w11perf;

DROP PROCEDURE IF EXISTS bench_overdue;

DELIMITER $$
CREATE PROCEDURE bench_overdue(IN variant INT, IN iters INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE t0 DATETIME(6);
    SET t0 = NOW(6);
    WHILE i < iters DO
        IF variant = 0 THEN
            -- 现状：status + end_time，无索引可用 → 全表（主键索引）扫描
            UPDATE reservation SET status = status
            WHERE status = 'PENDING' AND end_time < NOW();
        ELSE
            -- 改写：补 reserve_date <= CURDATE()，吃 idx_reservation_date_status 的范围扫描
            UPDATE reservation SET status = status
            WHERE status = 'PENDING' AND end_time < NOW() AND reserve_date <= CURDATE();
        END IF;
        SET i = i + 1;
    END WHILE;
    SELECT variant AS `variant(0=现状,1=改写)`, iters AS `iters`,
           ROUND(TIMESTAMPDIFF(MICROSECOND, t0, NOW(6)) / iters, 1) AS `avg_micros_per_exec`,
           (SELECT COUNT(*) FROM reservation) AS `table_rows`;
END$$
DELIMITER ;

CALL bench_overdue(0, 300);
CALL bench_overdue(1, 300);
