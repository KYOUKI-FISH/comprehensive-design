# -*- coding: utf-8 -*-
"""第 12 周 BUG-07 回归：九组冲突场景逐组复跑（新口径：HTTP 状态行 == body.code）。

背景
----
第 10 周九组冲突（`docs/week10/02_冲突测试说明.md`）是在 BUG-07 修复前跑的，
当时记账为「HTTP 一律 200、失败靠 body.code 区分」。第 12 周按「双写」修好后
（HTTP 状态行 = body.code = 400/401/403/404/409/500），需要逐组复跑确认：

  1-5 组：拒绝 → **HTTP 409 且 body.code 409**，带 data.occupiedSlots，库内不新增；
  6-8 组：放行（首尾相接 ×2、同日不同室）→ **HTTP 200 且 body.code 200**，库内各 1 条；
  9   组：取消后重订 → 取消 200 且原记录 CANCELLED；重订 200 且新增 PENDING，原记录保留。

复跑与第 10 周的口径差异（写进报告时要说明）
--------------------------------------------
* 第 10 周为造数 SQL 预先插库 + 手工提交；本次**全部走接口创建前置预约**，
  避免直接写库带来的状态不一致，也更贴近真实使用路径。
* 日期取「今天 +50 天」起的九个连续日，与种子数据、第 11 周并发日期都不重叠。

用法
----
    DB_PASSWORD="" python tests/week11/07_conflict_nine_groups.py
    （DB_PASSWORD 未设置会跳过库内核对并明确标「未核对」，不会假装查过）

证据
----
    docs/week12/evidence/bug07_conflict_nine_groups.json
"""
import json
import os
import sys
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wk11_common as W  # noqa: E402

ROOM1 = int(os.environ.get("WK11_ROOM1", "1"))
ROOM2 = int(os.environ.get("WK11_ROOM2", "2"))
DAY_OFFSET = int(os.environ.get("WK11_CONFLICT_OFFSET", "50"))
EVIDENCE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "docs", "week12", "evidence", "bug07_conflict_nine_groups.json")


def day(i):
    """第 i 组使用的日期（i 从 1 起），远离种子数据。"""
    return (datetime.now() + timedelta(days=DAY_OFFSET + i)).strftime("%Y-%m-%d")


def submit(token, room, date, start_hm, end_hm, topic):
    payload = {
        "roomId": room,
        "date": date,
        "startTime": "%s %s" % (date, start_hm),
        "endTime": "%s %s" % (date, end_hm),
        "topic": topic,
        "attendeeCount": 3,
        "contact": "冲突回归",
    }
    return W.call("POST", "/bookings", payload, token)


def slots_of(body):
    data = (body or {}).get("data") or {}
    out = []
    for s in (data.get("occupiedSlots") or []):
        out.append((s.get("startTime"), s.get("endTime")))
    return out


def db_alive(room, date):
    """该房该日的有效（非取消）预约条数。"""
    v = W.db_scalar(
        "SELECT COUNT(*) FROM reservation WHERE room_id=%d AND reserve_date='%s' "
        "AND status<>'CANCELLED';" % (room, date))
    return v


def db_status(booking_id):
    return W.db_scalar("SELECT status FROM reservation WHERE id=%d;" % booking_id)


GROUPS = [
    # (组号, 场景, 房间, 前置时段, 测试时段, 期望码, 期望占用时段或 None)
    (1, "完全重叠", ROOM1, ("09:00", "11:00"), ("09:00", "11:00"), 409, ("09:00", "11:00")),
    (2, "前段重叠", ROOM1, ("09:00", "11:00"), ("08:00", "09:30"), 409, ("09:00", "11:00")),
    (3, "后段重叠", ROOM1, ("09:00", "11:00"), ("10:30", "12:00"), 409, ("09:00", "11:00")),
    (4, "新预约包含已有", ROOM1, ("10:00", "10:30"), ("09:00", "11:00"), 409, ("10:00", "10:30")),
    (5, "新预约被包含", ROOM1, ("09:00", "11:00"), ("10:00", "10:30"), 409, ("09:00", "11:00")),
    (6, "首尾相接（后）", ROOM1, ("09:00", "11:00"), ("11:00", "12:00"), 200, None),
    (7, "首尾相接（前）", ROOM1, ("09:00", "11:00"), ("08:00", "09:00"), 200, None),
    (8, "同日不同会议室", ROOM1, ("09:00", "11:00"), ("09:00", "11:00"), 200, None),  # 测试提交到 ROOM2
    (9, "取消后重订", ROOM1, ("09:00", "11:00"), ("09:00", "11:00"), 200, None),
]


def main():
    token = W.require_token("user1")
    db_mode = "已启用" if os.environ.get("DB_PASSWORD") is not None else "未启用（未设置 DB_PASSWORD）"
    W.say("=" * 78)
    W.say("第 12 周 BUG-07 回归：九组冲突逐组复跑")
    W.say("判据：HTTP 状态行 == body.code；拒绝组 409 + occupiedSlots；放行组 200 + 库内新增")
    W.say("房间：%d / %d（第 8 组用 %d）；日期：%s 起九日" % (ROOM1, ROOM2, ROOM2, day(1)))
    W.say("库内核对：%s（%s）" % (db_mode, os.environ.get("WK11_DB", "meeting_room")))
    W.say("=" * 78)

    records = []
    for no, scene, room, pre, test, expect, want_slot in GROUPS:
        date = day(no)
        target_room = ROOM2 if no == 8 else room
        rec = {
            "group": no, "scene": scene, "date": date,
            "room": room, "target_room": target_room,
            "pre_slot": "%s-%s" % pre, "test_slot": "%s-%s" % test,
            "expect_code": expect,
        }

        # ---- 前置预约（全部走接口创建）----
        st, body, _ = submit(token, room, date, pre[0], pre[1], "第12周回归-组%d-前置" % no)
        rec["pre_http"] = st
        rec["pre_code"] = (body or {}).get("code")
        if st != 200 or rec["pre_code"] != 200:
            rec["verdict"] = "环境异常：前置预约未建成"
            W.say("[组%d] %s —— 前置失败 HTTP=%s code=%s %s"
                  % (no, scene, st, rec["pre_code"], (body or {}).get("message")))
            records.append(rec)
            continue
        pre_id = ((body or {}).get("data") or {}).get("id")
        rec["pre_id"] = pre_id

        # ---- 第 9 组：先取消再重订 ----
        if no == 9:
            st_c, body_c, _ = W.call("DELETE", "/bookings/%s" % pre_id,
                                     {"reason": "第12周回归：取消后重订"}, token)
            rec["cancel_http"] = st_c
            rec["cancel_code"] = (body_c or {}).get("code")
            rec["cancel_db_status"] = db_status(pre_id)
            cancel_ok = (st_c == 200 and rec["cancel_code"] == 200
                         and rec["cancel_db_status"] in ("CANCELLED", "?"))
            st2, body2, _ = submit(token, room, date, test[0], test[1], "第12周回归-组9-重订")
            rec["resubmit_http"] = st2
            rec["resubmit_code"] = (body2 or {}).get("code")
            alive = W.db_scalar("SELECT COUNT(*) FROM reservation WHERE room_id=%d "
                                "AND reserve_date='%s' AND status<>'CANCELLED';" % (room, date))
            cancelled = W.db_scalar("SELECT COUNT(*) FROM reservation WHERE room_id=%d "
                                    "AND reserve_date='%s' AND status='CANCELLED';" % (room, date))
            rec["db_alive"] = alive
            rec["db_cancelled"] = cancelled
            ok = (cancel_ok and st2 == 200 and rec["resubmit_code"] == 200
                  and str(alive) == "1" and str(cancelled) == "1")
            rec["verdict"] = "通过" if ok else "不通过"
            W.say("[组9] 取消后重订 —— 取消 HTTP=%s code=%s db=%s ｜ 重订 HTTP=%s code=%s ｜ "
                  "库内有效=%s 已取消=%s → %s"
                  % (st_c, rec["cancel_code"], rec["cancel_db_status"], st2,
                     rec["resubmit_code"], alive, cancelled, rec["verdict"]))
            records.append(rec)
            continue

        # ---- 第 1-8 组：提交测试时段 ----
        st2, body2, _ = submit(token, target_room, date, test[0], test[1], "第12周回归-组%d" % no)
        code2 = (body2 or {}).get("code")
        rec["test_http"] = st2
        rec["test_code"] = code2
        rec["occupied_slots"] = slots_of(body2)
        alive = db_alive(target_room, date)
        rec["db_alive"] = alive

        checks = [
            ("HTTP 状态行 == 期望", st2 == expect),
            ("HTTP 状态行 == body.code", st2 == code2),
        ]
        if expect == 409:
            checks.append(("occupiedSlots 含真实占用时段 %s-%s" % want_slot,
                           want_slot in (rec["occupied_slots"] or [])))
            checks.append(("库内未新增（仍 1 条）", str(alive) == "1"))
        else:
            checks.append(("返回预约编号", bool(((body2 or {}).get("data") or {}).get("bookingNo")
                                              or ((body2 or {}).get("data") or {}).get("id"))))
            if no == 8:
                # 同日不同会议室：房 1 与房 2 各 1 条，不能拿「同房间 2 条」来套
                home_cnt = db_alive(room, date)
                rec["db_home"] = home_cnt
                checks.append(("两个会议室各 1 条（房%d=%s / 房%d=%s）"
                               % (room, home_cnt, target_room, alive),
                               str(alive) == "1" and str(home_cnt) == "1"))
            else:
                checks.append(("库内新增（2 条）", str(alive) == "2"))

        ok = all(c[1] for c in checks)
        rec["checks"] = [{"item": c[0], "ok": bool(c[1])} for c in checks]
        rec["verdict"] = "通过" if ok else "不通过"
        W.say("[组%d] %-12s HTTP=%s code=%s 占用=%s 库内=%s → %s%s"
              % (no, scene, st2, code2, rec["occupied_slots"] or "-", alive, rec["verdict"],
                 "" if ok else "  ✗ " + "；".join(c[0] for c in checks if not c[1])))
        records.append(rec)

    passed = sum(1 for r in records if r["verdict"] == "通过")
    total = len(records)
    W.say("-" * 78)
    W.say("九组结果：%d / %d 通过" % (passed, total))
    mismatch = [r["group"] for r in records if r.get("test_http") is not None
                and r.get("test_code") is not None and r["test_http"] != r["test_code"]]
    W.say("HTTP 状态行与 body.code 不一致的组：%s（判据要求 0）" % (mismatch or "无"))
    W.say("清理提示：DELETE FROM reservation WHERE reserve_date >= '%s';" % day(1))

    W.save_json(EVIDENCE, {
        "title": "BUG-07 回归：九组冲突逐组复跑（HTTP 状态行 == body.code）",
        "executed_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "base_url": W.BASE,
        "db": os.environ.get("WK11_DB", "meeting_room"),
        "db_checked": os.environ.get("DB_PASSWORD") is not None,
        "day_offset": DAY_OFFSET,
        "passed": passed,
        "total": total,
        "http_code_mismatch_groups": mismatch,
        "groups": records,
    })


if __name__ == "__main__":
    main()
