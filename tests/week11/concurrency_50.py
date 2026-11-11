# -*- coding: utf-8 -*-
"""第 11 周硬指标：50 并发【同一秒】抢同一间会议室同一时段。

判据（任务书第三节 + 周推进计划 §2.1.2.1）：
  1. **恰好 1 条成功**（业务码 200）；
  2. **49 条明确失败**——HTTP 409 ＋ 冲突提示（body.code 409 且带 occupiedSlots），
     **不是 500、不是超时、不是空响应**；
  3. 库内该时段有效预约**只有 1 条**（要查库，光看返回值不够）；
  4. 50 条响应里**没有唯一键冲突报错**（验编号生成方案）；
  5. ★ BUG-07 回归：**每条响应 HTTP status == body.code**，分布恰为 200×1 ＋ 409×49，无 500。

★ 为什么用 threading.Barrier：
  "同一秒发起"不是"快速连续发"。Barrier 让 50 个线程全部就位后由一个闸门同时放行，
  用 for 循环顺序发等于把请求串行化，测不出超卖，任务书明确"不许顺序发"。
  脚本同时记录每条请求的**实际发出时刻**，起跑跨度 > 1 秒会在结论里标红。

用法：
    python tests/week11/concurrency_50.py            # 默认 50 并发
    python tests/week11/concurrency_50.py 10         # 10 并发（廉价复验，先跑这个）
"""
import os
import sys
import threading
import time
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wk11_common as W  # noqa: E402

CONCURRENCY = int(sys.argv[1]) if len(sys.argv) > 1 else 50
ROOM_ID = int(os.environ.get("WK11_ROOM_ID", "1"))
# 用一个远离种子数据的未来日期与空闲时段，避免与演示数据撞车
_day = datetime.now() + timedelta(days=int(os.environ.get("WK11_DAY_OFFSET", "40")))
DATE = _day.strftime("%Y-%m-%d")
START_H, END_H = os.environ.get("WK11_SLOT", "21:00-22:00").split("-")
START_TIME = "%s %s" % (DATE, START_H)
END_TIME = "%s %s" % (DATE, END_H)
PERF_DATE = DATE

token = W.require_token("user1")

results = [None] * CONCURRENCY
fired_at = [None] * CONCURRENCY
barrier = threading.Barrier(CONCURRENCY)

# 基线：跑之前该时段必须为空，否则测的不是"并发抢"而是"并发撞已有"
# ★ 必须用"时间重叠"计数，不能只按 room+date 数——灌了 1000 条之后同一天别的时段也有记录，
#   按 room+date 数会把无关记录算进来，得出"超卖"的假结论。
OVERLAP = ("room_id=%d AND reserve_date='%s' AND status<>'CANCELLED' "
           "AND start_time < '%s' AND end_time > '%s'") % (ROOM_ID, DATE, END_TIME, START_TIME)
before = W.db_scalar("SELECT COUNT(*) FROM reservation WHERE %s;" % OVERLAP)


def worker(i):
    payload = {
        "roomId": ROOM_ID,
        "date": DATE,
        "startTime": START_TIME,
        "endTime": END_TIME,
        "topic": "第11周并发-%02d" % i,
        "attendeeCount": 3,
        "contact": "并发测试",
    }
    barrier.wait()          # ★ 闸门：50 个线程同一时刻起跑
    fired_at[i] = time.time()
    results[i] = W.call("POST", "/bookings", payload, token)


W.say("=" * 78)
W.say("第 11 周并发超卖测试：%d 并发抢同一间会议室同一时段" % CONCURRENCY)
W.say("目标：room=%d，%s — %s" % (ROOM_ID, START_TIME, END_TIME))
W.say("库内基线：该日期已有预约 %s 条（应为 0 或与本次无关）" % before)
W.say("=" * 78)

threads = [threading.Thread(target=worker, args=(i,)) for i in range(CONCURRENCY)]
for t in threads:
    t.start()
for t in threads:
    t.join()

span_ms = (max(fired_at) - min(fired_at)) * 1000


def biz(r):
    """业务码：响应体里的 code。第 12 周 BUG-07 修复后，HTTP 状态码与它同值，
    这里仍分别统计（body.code 分类 + HTTP 分布 + 逐条一致性），报告里不混淆。"""
    return ((r[1] or {}).get("code") if r else None)


# ★ BUG-07 回归：逐条核对「HTTP status == body.code」，并单独盯住 500
mismatch = [(i, r[0], (r[1] or {}).get("code"))
            for i, r in enumerate(results) if not r or r[0] != (r[1] or {}).get("code")]
http_500 = [i for i, r in enumerate(results) if r and r[0] == 500]


success = [(i, r) for i, r in enumerate(results) if biz(r) == 200]
conflict = [(i, r) for i, r in enumerate(results) if biz(r) == 409]
others = [(i, r) for i, r in enumerate(results) if biz(r) not in (200, 409)]
uk_err = [r for _, r in others
          if "uk_reservation_no" in str(r[1]) or "Duplicate entry" in str(r[1])]
http_dist = {}
for r in results:
    key = r[0] if r else None
    http_dist[key] = http_dist.get(key, 0) + 1

W.say()
W.say("起跑跨度（%d 条请求的实际发出时刻差）：%.1f ms" % (CONCURRENCY, span_ms))
W.say("HTTP 状态码分布：%s（★ BUG-07 修复后应为 200×1 ＋ 409×%d）"
      % (", ".join("%s×%s" % (k, v) for k, v in sorted(http_dist.items(), key=lambda x: str(x[0]))),
         CONCURRENCY - 1))
W.say("HTTP status 与 body.code 不一致：%d 条（判据要求 0）；其中 HTTP 500：%d 条（判据要求 0）"
      % (len(mismatch), len(http_500)))
for i, status, code in mismatch[:5]:
    W.say("    #%02d HTTP=%s body.code=%s" % (i, status, code))
W.say("成功（业务码 200）  ：%d 条" % len(success))
for i, r in success:
    W.say("    #%02d 预约号 %s" % (i, (r[1].get("data") or {}).get("reservationNo")))
W.say("冲突（业务码 409）  ：%d 条" % len(conflict))
if conflict:
    msg = conflict[0][1][1].get("message")
    slots = (conflict[0][1][1].get("data") or {}).get("occupiedSlots") or []
    W.say("    提示：%s" % msg)
    W.say("    返回的占用时段：%s" % [
        "%s~%s" % ((s.get("startTime") or "")[-5:], (s.get("endTime") or "")[-5:]) for s in slots])
W.say("其他（500/超时/空）：%d 条（判据要求 0）" % len(others))
for i, r in others[:5]:
    W.say("    #%02d status=%s body=%s" % (i, r[0], str(r[1])[:160]))
W.say("其中唯一键冲突报错：%d 条（判据要求 0）" % len(uk_err))

# ---- 库内核对：返回值说得再好听，也要查库 ----
W.say()
W.say("--- 库内核对 ---")
rows = W.db(
    "SELECT reservation_no, user_id, DATE_FORMAT(start_time,'%%H:%%i'), "
    "DATE_FORMAT(end_time,'%%H:%%i'), status FROM reservation WHERE %s ORDER BY id;" % OVERLAP
)
if rows is None:
    W.say("⚠ 未设置 DB_PASSWORD，库内核对【已跳过】——本项不算完成，请设好环境变量后重跑。")
    db_count = None
else:
    db_count = len(rows)
    W.say("该日期该会议室有效预约：%d 条" % db_count)
    for r in rows:
        W.say("    %s user_id=%s %s-%s status=%s" % tuple(r))

no_rows = W.db("SELECT COUNT(DISTINCT reservation_no), COUNT(*) FROM reservation;")
if no_rows and not isinstance(no_rows, dict):
    W.say("全表编号去重数 / 总数 = %s / %s（相等即无撞键）" % tuple(no_rows[0]))

# ---- 判定 ----
W.say()
W.say("=" * 78)
reasons = []
if len(success) != 1:
    reasons.append("成功条数 %d ≠ 1" % len(success))
if len(conflict) != CONCURRENCY - 1:
    reasons.append("冲突条数 %d ≠ %d" % (len(conflict), CONCURRENCY - 1))
# ★ BUG-07 回归判据：HTTP 分布必须恰好 200×1 + 409×(N-1)，且没有 500 / 双写不一致
if http_dist.get(200) != 1 or http_dist.get(409) != CONCURRENCY - 1 or len(http_dist) != 2:
    reasons.append("HTTP 状态码分布 %s ≠ 200×1 + 409×%d"
                   % (http_dist, CONCURRENCY - 1))
if http_500:
    reasons.append("出现 %d 条 HTTP 500" % len(http_500))
if mismatch:
    reasons.append("HTTP status 与 body.code 不一致 %d 条（样例：%s）"
                   % (len(mismatch), mismatch[:3]))
if others:
    reasons.append("存在 %d 条非 200/409 响应" % len(others))
if db_count is not None and db_count != 1:
    reasons.append("库内有效预约 %d 条 ≠ 1" % db_count)
if uk_err:
    reasons.append("出现唯一键冲突报错 %d 条" % len(uk_err))
if span_ms > 1000:
    reasons.append("起跑跨度 %.0f ms > 1000 ms，不满足「同一秒」" % span_ms)

if reasons:
    W.say("判定：★ 未通过 —— " + "；".join(reasons))
else:
    W.say("判定：★ 通过 —— 1 条成功 / %d 条明确失败 / 库内仅 1 条 / 无撞键 / "
          "HTTP 200×1＋409×%d 且 status==code / 起跑跨度 %.0f ms"
          % (len(conflict), CONCURRENCY - 1, span_ms))
W.say("=" * 78)

W.save_json(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                 ".workbuddy", "reports", "week11_concurrency_%d.json" % CONCURRENCY),
    {
        "type": "concurrency",
        "concurrency": CONCURRENCY,
        "target": {"roomId": ROOM_ID, "date": DATE, "start": START_TIME, "end": END_TIME},
        "fired_span_ms": round(span_ms, 2),
        "success": [{"index": i, "no": (r[1].get("data") or {}).get("reservationNo")}
                    for i, r in success],
        "conflict_count": len(conflict),
        "http_status_distribution": {str(k): v for k, v in http_dist.items()},
        "status_code_mismatches": mismatch,
        "http_500_count": len(http_500),
        "conflict_sample": conflict[0][1][1] if conflict else None,
        "other_count": len(others),
        "others": [{"index": i, "status": r[0], "body": r[1]} for i, r in others],
        "unique_key_errors": len(uk_err),
        "db_rows": rows,
        "verdict": "PASS" if not reasons else "FAIL: " + "; ".join(reasons),
    },
)
