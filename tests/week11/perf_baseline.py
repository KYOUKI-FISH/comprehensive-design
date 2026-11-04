# -*- coding: utf-8 -*-
"""第 11 周性能基线：两个接口各 20 次取 P95，并附 EXPLAIN 结果（任务书第三节硬指标）。

被测接口（任务书点名这两个）：
  1. `GET /api/rooms?date=...`      —— 会议室列表（含当日占用时段聚合）
  2. `GET /api/bookings/mine`       —— 我的预约

判据：
  - 各 20 次成功响应（**不许只返回前 10 条**——接口本身不分页，这里也不加任何 limit）；
  - **P95 < 500ms**（任务书口径，比本组 NF-01 的 ≤2s 更严，取严）；
  - 附 `EXPLAIN`，看索引有没有走（`key` 列非 NULL；出现 `type=ALL` 即全表扫描＝不达标根因）。

用法：
    python tests/week11/perf_baseline.py [次数，默认 20]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wk11_common as W  # noqa: E402

ROUNDS = int(sys.argv[1]) if len(sys.argv) > 1 else 20
THRESHOLD = 500.0

token = W.require_token("user1")
total_rows = W.db_scalar("SELECT COUNT(*) FROM reservation;")
W.say("=" * 78)
W.say("第 11 周性能基线：2 个接口 × %d 次" % ROUNDS)
W.say("当前 reservation 条数：%s（任务书要求 ≥1000）" % total_rows)
W.say("=" * 78)

CASES = [
    ("GET /api/rooms", "/rooms?date=" + __import__("datetime").date.today().isoformat(), token),
    ("GET /api/bookings/mine", "/bookings/mine", token),
]

summary = {}
for name, path, tk in CASES:
    times = []
    last_body = None
    last_status = None
    for i in range(ROUNDS):
        status, body, ms = W.call("GET", path, None, tk)
        last_status, last_body = status, body
        if status != 200:
            W.say("第 %d 次失败：status=%s body=%s" % (i + 1, status, str(body)[:200]))
            continue
        times.append(ms)
    if not times:
        W.say("%s：无一次成功，无法计算 P95" % name)
        summary[name] = {"ok": False}
        continue
    data = (last_body or {}).get("data")
    returned = len(data) if isinstance(data, list) else "非列表"
    p95 = W.p95(times)
    summary[name] = {
        "rounds_ok": len(times),
        "min": round(min(times), 1),
        "max": round(max(times), 1),
        "avg": round(sum(times) / len(times), 1),
        "p95": round(p95, 1),
        "returned_rows": returned,
        "detail": [round(t, 1) for t in times],
        "pass": p95 < THRESHOLD,
    }
    W.say()
    W.say("【%s】成功 %d/%d 次，返回 %s 行" % (name, len(times), ROUNDS, returned))
    W.say("  耗时明细(ms)：%s" % ", ".join("%.1f" % t for t in times))
    W.say("  min=%.1f  avg=%.1f  max=%.1f  **P95=%.1f**  阈值 %.0f → %s"
          % (min(times), sum(times) / len(times), max(times), p95, THRESHOLD,
             "达标" if p95 < THRESHOLD else "★ 未达标"))

# ---- EXPLAIN：不附 explain 等于没做完 ----
W.say()
W.say("--- EXPLAIN（任务书要求：看索引有没有走）---")
EXPLAINS = [
    ("会议室列表 selectByCondition",
     "EXPLAIN SELECT id, name, location, capacity, equipment, status, remark, created_at, updated_at "
     "FROM meeting_room ORDER BY id;"),
    ("当日占用时段 selectOccupiedSlots",
     "EXPLAIN SELECT room_id, start_time, end_time FROM reservation "
     "WHERE reserve_date = '%s' AND status IN ('PENDING','ONGOING') AND room_id IN (1,2) "
     "ORDER BY room_id, start_time;" % __import__("datetime").date.today().isoformat()),
    ("我的预约 selectMine",
     "EXPLAIN SELECT r.id, r.reservation_no, r.room_id, m.name, r.reserve_date, r.start_time, "
     "r.end_time, r.status FROM reservation r JOIN meeting_room m ON m.id = r.room_id "
     "WHERE r.user_id = 2 ORDER BY r.start_time DESC, r.id DESC;"),
]
explain_lines = []
for label, sql in EXPLAINS:
    rows = W.db(sql)
    W.say()
    W.say("· %s" % label)
    if rows is None:
        W.say("  ⚠ 未设置 DB_PASSWORD，EXPLAIN 跳过（本项不算完成）")
        continue
    if isinstance(rows, dict):
        W.say("  执行失败：%s" % str(rows.get("error"))[:200])
        continue
    for r in rows:
        # EXPLAIN 列序：id, select_type, table, partitions, type, possible_keys, key, key_len, ref, rows, filtered, Extra
        W.say("  table=%s type=%s key=%s rows=%s Extra=%s"
              % (r[2] if len(r) > 2 else "?", r[4] if len(r) > 4 else "?",
                 r[6] if len(r) > 6 else "?", r[9] if len(r) > 9 else "?",
                 r[11] if len(r) > 11 else "?"))
        explain_lines.append({"label": label, "type": r[4] if len(r) > 4 else None,
                              "key": r[6] if len(r) > 6 else None,
                              "rows": r[9] if len(r) > 9 else None,
                              "extra": r[11] if len(r) > 11 else None})

W.say()
W.say("=" * 78)
bad = [k for k, v in summary.items() if not v.get("pass")]
if bad:
    W.say("判定：★ 未达标 —— %s（如实写进报告并说明根因）" % "、".join(bad))
else:
    W.say("判定：★ 通过 —— 两个接口 P95 均 < %.0f ms" % THRESHOLD)
W.say("=" * 78)

W.save_json(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                 ".workbuddy", "reports", "week11_perf.json"),
    {"rounds": ROUNDS, "threshold_ms": THRESHOLD, "rows_baseline": total_rows,
     "summary": summary, "explain": explain_lines},
)
