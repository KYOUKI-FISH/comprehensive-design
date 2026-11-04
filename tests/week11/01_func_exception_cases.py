# -*- coding: utf-8 -*-
"""第 11 周 功能／异常类用例 TC-01～TC-06 执行脚本（虞业宸）。

★ 为什么用脚本而不是"页面上点一遍"：
  任务书第三节要求"攻击／并发必须真实发出请求并留存响应"，用例同理——
  脚本把【请求】【响应】【库内核对】三样都留成文本，比截图可复核。

★ 每个用例用【不同日期】，互不干扰：
  第 9／10 周的历史数据里有大量 09:00—12:00 的占用，共用日期会让
  "预期成功"的用例莫名撞冲突（上周就踩过，见 docs/week10/06 记录）。

用法（后端与 MySQL 已启动）：
    python tests/week11/01_func_exception_cases.py
输出：
    控制台逐条结果 ＋ tests/week11/out/tc_result.json（供报告引用）
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta

# ★ 必须绕过本机代理：沙箱设了 HTTP(S)_PROXY 指向 127.0.0.1，urllib 会走代理，
#   代理转发不到本机后端 → 报 502，看起来像"后端挂了"。
for _k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    os.environ.pop(_k, None)
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = os.environ.get("BASE_URL", "http://localhost:8080/api")
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
MYSQL = os.path.expanduser("~/.workbuddy/binaries/mysql/mysql-8.0.46-winx64/bin/mysql.exe")
RESULTS = []

# 基准日往后推 40 天，避开第 9／10 周遗留的历史占用
BASE_DATE = (datetime.now() + timedelta(days=40)).date()


def d(offset):
    """基准日 + offset 天（每个用例独占一天）。"""
    return (BASE_DATE + timedelta(days=offset)).strftime("%Y-%m-%d")


def call(method, path, body=None, token=None, timeout=20):
    """发一次请求，返回 (http_status, payload)。payload 解析失败返回 {"raw": ...}。"""
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}
    except Exception as e:  # 网络层（后端没起、代理没绕过）
        return None, {"error": str(e)}


def login(username, password="123456"):
    st, payload = call("POST", "/auth/login", {"username": username, "password": password})
    assert st == 200 and payload.get("code") == 200, "登录失败 %s: %s" % (username, payload)
    data = payload["data"]
    return data["token"], data.get("userId") or data.get("user", {}).get("id")


def booking(room_id, date, start, end, topic, attendees=4, contact="虞业宸"):
    return {
        "roomId": room_id,
        "date": date,
        "startTime": "%s %s" % (date, start),
        "endTime": "%s %s" % (date, end),
        "topic": topic,
        "attendeeCount": attendees,
        "contact": contact,
    }


def mine_contains(token, topic):
    """我的预约里找 topic 对应的条目（返回 dict 或 None）。"""
    st, payload = call("GET", "/bookings/mine", token=token)
    if st != 200 or payload.get("code") != 200:
        return None
    for item in payload.get("data") or []:
        if item.get("topic") == topic:
            return item
    return None


def record(no, category, name, steps, expected, actual, passed, evidence):
    RESULTS.append({
        "no": no, "category": category, "name": name,
        "steps": steps, "expected": expected, "actual": actual,
        "conclusion": "通过" if passed else "未通过",
        "evidence": evidence, "executedAt": datetime.now().isoformat(timespec="seconds"),
    })
    print("[%s] %-6s %-24s %s" % (no, category, name, "通过" if passed else "未通过 → " + actual))


def cleanup_previous():
    """清掉上一轮本脚本写入的用例数据（topic 前缀 W11-TC）。

    ★ 为什么必须清：用例要可重复执行。上一轮 TC-05 留下的 09:00-10:00 / 10:00-11:00
    会让这一轮"预期成功"的用例撞冲突，看起来像代码退化，其实是脏数据。
    只删 topic 前缀为本脚本的测试行，不碰其它任何数据。
    """
    if not os.path.exists(MYSQL):
        print("（未找到 mysql 客户端，跳过清理；若上一轮数据仍在，TC-05 可能误报冲突）")
        return
    subprocess.run([MYSQL, "-uroot", "-P3306", "-h127.0.0.1", "-e",
                    "use meeting_room; delete from reservation where topic like 'W11-TC%';"],
                   capture_output=True, timeout=20)


def main():
    cleanup_previous()
    user_token, user_id = login("user1")
    admin_token, _ = login("admin")
    print("user1 id=%s  基准日=%s\n" % (user_id, BASE_DATE))

    # ---------------- TC-01 功能：普通用户提交 09:00—11:00 ----------------
    date = d(0)
    st, payload = call("POST", "/bookings", booking(1, date, "09:00:00", "11:00:00", "W11-TC01"), user_token)
    ok = st == 200 and payload.get("code") == 200 and payload["data"].get("reservationNo")
    tc01 = payload["data"] if ok else {}
    in_mine = mine_contains(user_token, "W11-TC01") if ok else None
    ok = ok and in_mine is not None
    record("TC-01", "功能", "提交 09:00—11:00 预约",
           "user1 登录 → POST /api/bookings（room1，%s 09:00-11:00）→ GET /api/bookings/mine" % date,
           "提示成功；我的预约出现该条；库内可查",
           "HTTP %s，编号 %s，mine 中%s" % (st, tc01.get("reservationNo"),
                                          "可见（状态 %s）" % in_mine.get("status") if in_mine else "不可见"),
           ok, {"request": {"method": "POST", "path": "/api/bookings",
                            "body": booking(1, date, "09:00:00", "11:00:00", "W11-TC01")},
                "response": {"status": st, "body": payload},
                "bookingId": tc01.get("id")})

    # ---------------- TC-02 功能：管理员强制取消（填原因） ----------------
    if tc01.get("id"):
        reason = "第11周用例：该预约属测试数据，强制取消"
        st, payload = call("PUT", "/admin/bookings/%d/cancel" % tc01["id"], {"reason": reason}, admin_token)
        status_now = (payload.get("data") or {}).get("status") if isinstance(payload.get("data"), dict) else None
        ok = st == 200 and payload.get("code") == 200 and status_now == "CANCELLED"
        # 日志可查：按 operationType 过滤
        st_l, logs = call("GET", "/admin/logs?operationType=BOOKING_FORCE_CANCEL&pageSize=5", token=admin_token)
        log_items = (logs.get("data") or {}).get("items") or (logs.get("data") or {}).get("list") or []
        has_log = any(str(tc01.get("reservationNo")) in json.dumps(x, ensure_ascii=False) for x in log_items)
        ok = ok and has_log
        record("TC-02", "功能", "管理员强制取消（填原因）",
               "admin 登录 → PUT /api/admin/bookings/%d/cancel {\"reason\":...} → GET /api/admin/logs" % tc01["id"],
               "提示成功；状态变已取消；操作日志可查",
               "HTTP %s，状态=%s，日志%s" % (st, status_now, "命中 %d 条" % len(log_items) if has_log else "未命中"),
               ok, {"response": {"status": st, "body": payload},
                    "logQuery": {"status": st_l, "count": len(log_items), "items": log_items}})
    else:
        record("TC-02", "功能", "管理员强制取消（填原因）", "-", "-", "TC-01 未产出预约，无法执行", False, {})

    # ---------------- TC-03 异常：跨天 ----------------
    date = d(2)
    body = booking(1, date, "23:00:00", "01:00:00", "W11-TC03")
    body["endTime"] = "%s 01:00:00" % d(3)   # 结束时间落在次日 → 跨天
    st, payload = call("POST", "/bookings", body, user_token)
    ok = st is not None and payload.get("code") == 400 and "跨天" in (payload.get("message") or "")
    still = mine_contains(user_token, "W11-TC03")
    ok = ok and still is None
    record("TC-03", "异常", "提交跨天预约",
           "POST /api/bookings（%s 23:00 → %s 01:00）" % (date, d(3)),
           "提示不能跨天，不入库",
           "HTTP %s code=%s message=%s；mine 中%s" % (st, payload.get("code"), payload.get("message"),
                                                   "未出现" if still is None else "竟出现"),
           ok, {"request": body, "response": {"status": st, "body": payload}})

    # ---------------- TC-04 异常：结束早于开始 ----------------
    date = d(3)
    body = booking(1, date, "15:00:00", "14:00:00", "W11-TC04")
    st, payload = call("POST", "/bookings", body, user_token)
    ok = st is not None and payload.get("code") == 400 and "结束时间必须晚于开始时间" in (payload.get("message") or "")
    ok = ok and mine_contains(user_token, "W11-TC04") is None
    record("TC-04", "异常", "结束时间早于开始",
           "POST /api/bookings（%s 15:00 → 14:00）" % date,
           "提示时间不合法，不入库",
           "HTTP %s code=%s message=%s" % (st, payload.get("code"), payload.get("message")),
           ok, {"request": body, "response": {"status": st, "body": payload}})

    # ---------------- TC-05 异常（边界正确路径）：首尾相接 ----------------
    date = d(4)
    st1, p1 = call("POST", "/bookings", booking(1, date, "09:00:00", "10:00:00", "W11-TC05-A"), user_token)
    st2, p2 = call("POST", "/bookings", booking(1, date, "10:00:00", "11:00:00", "W11-TC05-B"), user_token)
    ok = (st1 == 200 and p1.get("code") == 200) and (st2 == 200 and p2.get("code") == 200)
    record("TC-05", "异常", "首尾相接时段（半开区间）",
           "同一房间同一天先订 09:00-10:00，再订 10:00-11:00",
           "两条都提交成功（首尾相接不算冲突，FR-09）",
           "第一条 HTTP %s / 第二条 HTTP %s（code=%s message=%s）" % (
               st1, st2, p2.get("code"), p2.get("message")),
           ok, {"first": {"status": st1, "body": p1}, "second": {"status": st2, "body": p2}})

    # ---------------- TC-06 异常：已停用会议室 ----------------
    date = d(5)
    body = booking(3, date, "09:00:00", "10:00:00", "W11-TC06")   # room 3 = 学生活动中心 C102，DISABLED
    st, payload = call("POST", "/bookings", body, user_token)
    msg = payload.get("message") or ""
    ok = st is not None and payload.get("code") in (400, 404) and ("停用" in msg or "不可预约" in msg)
    ok = ok and mine_contains(user_token, "W11-TC06") is None
    record("TC-06", "异常", "对已停用会议室提交预约",
           "POST /api/bookings（roomId=3，库内 status=DISABLED）",
           "拒绝并提示不可预约，不入库",
           "HTTP %s code=%s message=%s" % (st, payload.get("code"), msg),
           ok, {"request": body, "response": {"status": st, "body": payload}})

    # ---------------- 汇总 ----------------
    passed = sum(1 for r in RESULTS if r["conclusion"] == "通过")
    print("\n合计 %d 条，通过 %d 条，未通过 %d 条" % (len(RESULTS), passed, len(RESULTS) - passed))
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "tc_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"executedAt": datetime.now().isoformat(timespec="seconds"),
                   "baseUrl": BASE, "baseDate": str(BASE_DATE),
                   "passed": passed, "total": len(RESULTS), "cases": RESULTS},
                  f, ensure_ascii=False, indent=2)
    print("结果已写入 %s" % out)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
