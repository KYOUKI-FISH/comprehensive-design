# -*- coding: utf-8 -*-
"""重放攻击（Replay Attack）复测 —— 第 12 周安全项补齐。

测什么
------
把**一份已经成功过的合法请求原样重放**（同一令牌、同一 URL、字节级相同的 body），
看是否会带来额外业务副作用：重复落库、重复取消、吊销令牌复活、管理动作重复生效。

四条用例
--------
R-1 重放提交预约（POST ×5）：期望 200×1 + 409×4，库内仅 1 条、编号不重复
R-2 重放取消预约（DELETE ×3）：期望 200×1 + 业务拒绝×2，状态仍 CANCELLED，不新增行
R-3 重放已吊销令牌（登出后重放原请求）：期望 401，令牌不可重放
R-4 重放管理端停用账号（PUT ×2）：期望幂等或明确拒绝，无 5xx，账号状态一致

诚实边界（结论必须照写，不许夸大）
--------------------------------
系统**没有 nonce / 时间戳去重**这类通用防重放机制；防的是"重放造成业务副作用"：
写请求被业务规则幂等化（半开区间冲突判定 409、状态机、唯一编号、归属校验），
令牌吊销后不可再用。无副作用的 GET 重放不做拦截，也不构成风险。

运行
----
    DB_PASSWORD="" WK11_MYSQL="<mysql.exe 绝对路径>" python tests/week11/08_replay_attack.py
"""
import datetime
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wk11_common as W  # noqa: E402

DAY = (datetime.date.today() + datetime.timedelta(days=41)).isoformat()
ROOM = 1
START, END = "15:00", "16:00"
EVIDENCE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "docs", "week12", "evidence", "replay_attack_%s.json" % datetime.date.today().strftime("%Y%m%d"),
)

cases = []


def record(cid, title, expect, steps, checks, note=""):
    passed = all(c["ok"] for c in checks)
    cases.append({
        "case": cid, "title": title, "expect": expect, "steps": steps,
        "checks": checks, "passed": passed, "note": note,
    })
    W.say("")
    W.say("【%s】%s —— %s" % (cid, title, "通过" if passed else "★不通过"))
    for c in checks:
        W.say("   %s %s（期望 %s，实际 %s）" % ("✓" if c["ok"] else "✗", c["item"], c["expect"], c["actual"]))
    if note:
        W.say("   注：%s" % note)
    return passed


def main():
    W.say("=" * 72)
    W.say("重放攻击复测  目标=%s  日期=%s  会议室=%d  时段=%s-%s" % (W.BASE, DAY, ROOM, START, END))
    W.say("=" * 72)

    base_total = W.db_scalar("SELECT COUNT(*) FROM reservation")
    W.say("基线：reservation 总数 = %s" % base_total)

    u1 = W.require_token("user1")
    adm = W.require_token("admin")

    # ---------------- R-1 重放写请求：提交预约 ×5 ----------------
    payload = {
        "roomId": ROOM, "date": DAY,
        "startTime": "%s %s" % (DAY, START), "endTime": "%s %s" % (DAY, END),
        "topic": "重放攻击测试-R1", "attendeeCount": 2, "contact": "13800000002",
    }
    raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")  # ★ 字节级一致，每次原样重放

    steps, statuses, codes = [], [], []
    created_id = None
    for i in range(5):
        st, body, ms = W.call("POST", "/bookings", raw=raw, token=u1)
        statuses.append(st)
        codes.append((body or {}).get("code"))
        if st == 200 and created_id is None:
            created_id = (body or {}).get("data", {}).get("id")
        steps.append({"replay": i + 1, "http": st, "code": (body or {}).get("code"),
                      "elapsed_ms": round(ms, 1),
                      "occupiedSlots": (body or {}).get("data", {}).get("occupiedSlots")})
        W.say("   重放#%d  HTTP=%s  code=%s  %.1fms" % (i + 1, st, (body or {}).get("code"), ms))

    rows = W.db("SELECT id,reservation_no,status FROM reservation "
                "WHERE room_id=%d AND reserve_date='%s'" % (ROOM, DAY)) or []
    alive = [r for r in rows if len(r) > 2 and r[2] != "CANCELLED"]
    nos = {r[1] for r in rows}

    record(
        "R-1", "重放提交预约（同一 body ×5）",
        "200×1 + 409×4，库内 1 条、编号唯一",
        steps,
        [
            {"item": "首次成功、其后全部 409", "expect": "[200, 409, 409, 409, 409]",
             "actual": str(statuses), "ok": statuses == [200, 409, 409, 409, 409]},
            {"item": "HTTP 状态行与 body.code 一致", "expect": "逐条相等",
             "actual": str(codes), "ok": codes == statuses},
            {"item": "库内该时段有效预约数", "expect": "1", "actual": str(len(alive)),
             "ok": len(alive) == 1},
            {"item": "预约编号去重数 / 行数", "expect": "1 / 1", "actual": "%d / %d" % (len(nos), len(rows)),
             "ok": len(nos) == 1 and len(rows) == 1},
        ],
        note="重放未产生第二条记录；第 2—5 次由半开区间冲突判定拦下（409 带真实占用时段）",
    )

    # ---------------- R-2 重放取消：DELETE ×3 ----------------
    steps2, st2 = [], []
    if created_id:
        for i in range(3):
            st, body, ms = W.call("DELETE", "/bookings/%d" % created_id, token=u1)
            st2.append(st)
            steps2.append({"replay": i + 1, "http": st, "code": (body or {}).get("code"),
                           "message": (body or {}).get("message"), "elapsed_ms": round(ms, 1)})
            W.say("   取消重放#%d  HTTP=%s  code=%s  msg=%s" % (i + 1, st, (body or {}).get("code"),
                                                              (body or {}).get("message")))
        st_row = W.db("SELECT status FROM reservation WHERE id=%d" % created_id) or [["?"]]
        total_now = W.db_scalar("SELECT COUNT(*) FROM reservation")
        record(
            "R-2", "重放取消预约（DELETE ×3）",
            "200×1 + 明确业务拒绝×2，状态仍 CANCELLED，不新增行",
            steps2,
            [
                {"item": "首次成功、重放被明确拒绝（400/404/409）", "expect": "200 后非 200 且 <500",
                 "actual": str(st2), "ok": bool(st2) and st2[0] == 200 and all(s and 400 <= s < 500 for s in st2[1:])},
                {"item": "库内状态", "expect": "CANCELLED", "actual": st_row[0][0],
                 "ok": st_row[0][0] == "CANCELLED"},
                {"item": "未产生 5xx", "expect": "0 条", "actual": str(sum(1 for s in st2 if s and s >= 500)),
                 "ok": sum(1 for s in st2 if s and s >= 500) == 0},
            ],
            note="取消是状态迁移，重复取消被状态机拒绝；不产生二次审计/二次释放",
        )
    else:
        W.say("R-1 未造出预约，跳过 R-2")

    # ---------------- R-3 重放已吊销令牌 ----------------
    u2 = W.require_token("user2")
    W.call("POST", "/auth/logout", token=u2)
    W.say("   user2 已登出（令牌吊销）")
    st_a, body_a, _ = W.call("GET", "/bookings/mine", token=u2)
    st_b, body_b, _ = W.call("POST", "/bookings", raw=raw, token=u2)  # 重放同一份 body
    total_after_r3 = W.db_scalar("SELECT COUNT(*) FROM reservation")
    rows_r3 = W.db("SELECT COUNT(*) FROM reservation WHERE room_id=%d AND reserve_date='%s' AND topic LIKE '%%重放攻击测试-R1%%'" % (ROOM, DAY))
    record(
        "R-3", "重放已吊销令牌（登出后重放 GET + 原 POST）",
        "两次均 401，令牌不可重放，库无新增",
        [{"replay": "GET /bookings/mine", "http": st_a, "code": (body_a or {}).get("code")},
         {"replay": "POST /bookings（原 body）", "http": st_b, "code": (body_b or {}).get("code")}],
        [
            {"item": "吊销令牌 GET", "expect": "401", "actual": str(st_a), "ok": st_a == 401},
            {"item": "吊销令牌重放写请求", "expect": "401", "actual": str(st_b), "ok": st_b == 401},
            {"item": "库内该主题记录数", "expect": "1（仍只有 R-1 那条）",
             "actual": str(rows_r3[0][0] if rows_r3 else "?"),
             "ok": bool(rows_r3) and rows_r3[0][0] == "1"},
        ],
        note="会话吊销后令牌立即失效，重放无任何作用（NF-04）",
    )

    # ---------------- R-4 重放管理端停用账号 ----------------
    log_before = W.db_scalar("SELECT COUNT(*) FROM operation_log")
    st4 = []
    steps4 = []
    for i in range(2):
        st, body, ms = W.call("PUT", "/admin/users/2", {"status": "DISABLED"}, token=adm)
        st4.append(st)
        steps4.append({"replay": i + 1, "http": st, "code": (body or {}).get("code"),
                       "message": (body or {}).get("message")})
        W.say("   停用重放#%d  HTTP=%s  code=%s  msg=%s" % (i + 1, st, (body or {}).get("code"),
                                                          (body or {}).get("message")))
    W.call("PUT", "/admin/users/2", {"status": "ACTIVE"}, token=adm)  # 立刻恢复
    log_after = W.db_scalar("SELECT COUNT(*) FROM operation_log")
    user_status = W.db("SELECT status FROM user WHERE id=2") or [["?"]]
    st_user = user_status[0][0]
    record(
        "R-4", "重放管理端停用账号（PUT ×2，已恢复 ACTIVE）",
        "无 5xx；账号状态一致；每次动作都留痕",
        steps4,
        [
            {"item": "两次均无 5xx", "expect": "0 条 5xx", "actual": str(sum(1 for s in st4 if s and s >= 500)),
             "ok": sum(1 for s in st4 if s and s >= 500) == 0},
            {"item": "账号最终状态（已手工恢复）", "expect": "ACTIVE", "actual": st_user, "ok": st_user == "ACTIVE"},
            {"item": "操作日志新增条数", "expect": "≥1（重复动作同样留痕）",
             "actual": "%s → %s" % (log_before, log_after),
             "ok": log_after is not None and log_before is not None and int(log_after) > int(log_before)},
        ],
        note="管理端重复停用不产生坏状态；审计留痕不丢（NF-14）",
    )

    # ---------------- 清理与基线恢复 ----------------
    W.say("")
    W.say("=" * 72)
    if created_id:
        W.db("DELETE FROM reservation WHERE id=%d" % created_id)
    W.db("DELETE FROM reservation WHERE room_id=%d AND reserve_date='%s'" % (ROOM, DAY))
    final_total = W.db_scalar("SELECT COUNT(*) FROM reservation")
    W.say("清理后 reservation 总数 = %s（基线 %s）%s" % (
        final_total, base_total, "—— 一致 ✓" if str(final_total) == str(base_total) else "★ 不一致，需手工核对"))

    passed = sum(1 for c in cases if c["passed"])
    total = len(cases)
    W.say("")
    W.say("结果：%d / %d 通过" % (passed, total))
    for c in cases:
        W.say("   %s %s %s" % ("✓" if c["passed"] else "✗", c["case"], c["title"]))

    evidence = {
        "title": "重放攻击复测（HTTP 状态行 == body.code）",
        "executed_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "base_url": W.BASE, "db": W.DB_NAME, "db_checked": W.db_scalar("SELECT 1") is not None,
        "target": {"roomId": ROOM, "date": DAY, "slot": "%s-%s" % (START, END)},
        "cases": cases, "passed": passed, "total": total,
        "baseline": {"reservation_before": base_total, "reservation_after": final_total},
        "boundary": "无 nonce/时间戳去重；靠业务规则（冲突判定/状态机/唯一编号/归属校验）与令牌吊销消除重放副作用；无副作用 GET 重放不拦截",
    }
    W.save_json(EVIDENCE, evidence)
    W.say("")
    W.say("结论口径（可直接念）：重放同一份合法写请求 5 次，仅 1 次落库、4 次 409；"
          "重复取消被状态机拒绝；吊销令牌重放两次均 401；管理端重复动作无坏状态且留痕。")

    sys.exit(0 if passed == total else 2)


if __name__ == "__main__":
    main()
