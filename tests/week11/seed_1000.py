# -*- coding: utf-8 -*-
"""第 11 周性能基线第一步：给 reservation 表灌 1000 条预约（任务书第三节要求）。

★ 为什么不减数据量：任务书写明"不许减少数据量"。1000 条是**硬指标**，
  减成 100 条测出来的 P95 没有意义（这是实测可复现性的一部分）。
★ 为什么用 SQL 直灌而不是调 1000 次接口：性能基线测的是**查询侧**（GET /api/rooms、
  GET /api/bookings/mine），造数走数据库不改变被测口径；1000 次 HTTP 只会把时间耗在造数上。
★ 编号仍守 `MR + yyyyMMdd + 左补零主键` 的派生规则：显式给 id，编号由 id 算出，
  灌完之后全表编号仍满足"主键单射"，不会污染编号唯一性验证。

用法：
    set DB_PASSWORD=...        # Windows；Linux/macOS 用 export
    python tests/week11/seed_1000.py [条数，默认 1000]
"""
import os
import sys
from datetime import date, datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wk11_common as W  # noqa: E402

TARGET = int(sys.argv[1]) if len(sys.argv) > 1 else 1000

if not os.environ.get("DB_PASSWORD"):
    W.say("★ 必须设置环境变量 DB_PASSWORD（脚本不内置口令）后重跑。")
    sys.exit(1)

before = W.db_scalar("SELECT COUNT(*) FROM reservation;")
W.say("灌数前 reservation 总条数：%s" % before)

if int(before) >= TARGET:
    W.say("库里已有 %s 条 ≥ %d 条，判定为已灌过，跳过（要重灌请先恢复基线库）。" % (before, TARGET))
    sys.exit(0)

max_id = int(W.db_scalar("SELECT COALESCE(MAX(id),0) FROM reservation;"))
user_ids = [r[0] for r in (W.db("SELECT id FROM user ORDER BY id;") or [])]
room_ids = [r[0] for r in (W.db("SELECT id FROM meeting_room WHERE status='ENABLED' ORDER BY id;") or [])]
if not user_ids or not room_ids:
    W.say("取不到用户或启用中的会议室，放弃灌数。")
    sys.exit(1)

need = TARGET - int(before)
W.say("需要新增 %d 条；用户 %s，启用会议室 %s，起始 id=%d" % (need, user_ids, room_ids, max_id + 1))

base_day = date.today()
rows = []
next_id = max_id + 1
# 先补齐到 1000 条：优先把「每个房间 × 每天 × 每个小时」铺满，再循环复用
for i in range(need):
    rid = next_id
    next_id += 1
    room = room_ids[i % len(room_ids)]
    user = user_ids[(i // len(room_ids)) % len(user_ids)]
    day = base_day + timedelta(days=(i // (len(room_ids) * 10)) % 90 + 1)
    hour = 8 + (i // len(room_ids)) % 10          # 08:00 ~ 17:00 起
    start = datetime(day.year, day.month, day.day, hour, 0, 0)
    end = start + timedelta(hours=1)
    no = "MR%s%03d" % (day.strftime("%Y%m%d"), rid)
    # 状态分布：以历史/未来混排，贴近真实（多数待开始，少量已取消）
    status = "CANCELLED" if i % 17 == 0 else ("PENDING" if day >= base_day else "FINISHED")
    rows.append(
        "(%d,'%s',%s,%s,'%s','%s','%s','性能基线造数%03d',%d,'perf',%s)"
        % (rid, no, user, room, day.isoformat(), start.strftime("%Y-%m-%d %H:%M:%S"),
           end.strftime("%Y-%m-%d %H:%M:%S"), i, 2 + i % 18,
           "'%s'" % status)
    )

sql_head = ("INSERT INTO reservation (id, reservation_no, user_id, room_id, reserve_date, "
            "start_time, end_time, topic, attendee_count, contact, status) VALUES ")
ok = 0
BATCH = 200
for i in range(0, len(rows), BATCH):
    chunk = rows[i:i + BATCH]
    res = W.db(sql_head + ",".join(chunk) + ";")
    if res is None:
        W.say("db() 未返回结果（口令问题），中止。")
        sys.exit(1)
    if isinstance(res, dict):
        W.say("第 %d 批失败：%s" % (i // BATCH + 1, res.get("error", "")[:300]))
        W.say("已成功写入 %d 条，请恢复基线库后重跑。" % ok)
        sys.exit(1)
    ok += len(chunk)
    W.say("已写入 %d / %d 条" % (ok, need))

after = W.db_scalar("SELECT COUNT(*) FROM reservation;")
distinct = W.db_scalar("SELECT COUNT(DISTINCT reservation_no) FROM reservation;")
W.say()
W.say("灌数后：reservation 总条数 = %s，编号去重数 = %s（两者相等即无重复编号）" % (after, distinct))
W.say("近 90 天分布：")
for r in (W.db("SELECT reserve_date, COUNT(*) FROM reservation GROUP BY reserve_date "
               "ORDER BY reserve_date DESC LIMIT 5;") or []):
    W.say("    %s -> %s 条" % tuple(r))

W.save_json(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                 ".workbuddy", "reports", "week11_seed.json"),
    {"before": before, "inserted": ok, "after": after,
     "distinct_no": distinct, "target": TARGET},
)
