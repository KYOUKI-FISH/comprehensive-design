# -*- coding: utf-8 -*-
"""第 11 周 SQL 注入攻击取证脚本（虞业宸）。

★ 安全边界（任务书第三节，红线）：
  所有请求目标【只能是本机】localhost:8080 的测试环境；脚本里 BASE 写死本机，
  发现非本机地址直接退出——禁止对学校在用系统、其他课程系统或互联网服务发起注入。

★ 为什么"打不动"也是结论：
  后端用 MyBatis 参数绑定（#{}），全仓库无 ${} 拼接，理论上天然防注入。
  本脚本的意义是【证明这一点】：载荷被当普通字符串处理（查不到／原样入库），
  服务不报错、表结构与数据完好。不是非要打出漏洞才算做完。

★ 取证三样（每类都要）：
  1. 请求：完整 URL／方法／Header／Body —— 落盘到 out/injection_result.json
  2. 响应：状态码 ＋ 响应体 —— 同上
  3. 库内数据：执行前后 SELECT 结果对比 —— db_snapshot() 采集（需要本机 mysql 客户端）

用法：
    python tests/week11/03_sql_injection.py
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

for _k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    os.environ.pop(_k, None)
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = os.environ.get("BASE_URL", "http://localhost:8080/api")
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
MYSQL = os.path.expanduser("~/.workbuddy/binaries/mysql/mysql-8.0.46-winx64/bin/mysql.exe")
EVIDENCE = []

# ★ 安全边界自检
assert "localhost" in BASE or "127.0.0.1" in BASE, "注入测试只允许打本机：%s" % BASE

PAYLOADS = [
    ("P1", "' OR '1'='1"),
    ("P2", "admin' -- "),
    ("P3", "' OR 1=1 #"),
    ("P4", "1; DROP TABLE reservation-- "),
]


def call(method, path, body=None, token=None, raw_body=None, timeout=20):
    url = BASE + path
    data = None
    if raw_body is not None:
        data = raw_body.encode("utf-8")
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
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
    except Exception as e:
        return None, {"error": str(e)}


def login(username, password="123456"):
    st, payload = call("POST", "/auth/login", {"username": username, "password": password})
    assert st == 200 and payload.get("code") == 200, "登录失败：%s" % payload
    return payload["data"]["token"]


def db_scalar(sql):
    """执行一条 SQL 取标量（用于"执行前后对比"）。没有客户端则返回 None。"""
    if not os.path.exists(MYSQL):
        return None
    try:
        out = subprocess.run([MYSQL, "-uroot", "-P3306", "-h127.0.0.1", "-N", "-B",
                              "-e", "use meeting_room; " + sql],
                             capture_output=True, timeout=20)
        return out.stdout.decode("utf-8", "ignore").strip()
    except Exception:
        return None


def snapshot():
    return {
        "reservationRows": db_scalar("select count(*) from reservation"),
        "userRows": db_scalar("select count(*) from user"),
        "roomRows": db_scalar("select count(*) from meeting_room"),
        "reservationTableExists": db_scalar(
            "select count(*) from information_schema.tables "
            "where table_schema='meeting_room' and table_name='reservation'"),
    }


def add(group, payload_id, payload, request, response, passed, note):
    EVIDENCE.append({
        "group": group, "payloadId": payload_id, "payload": payload,
        "request": request, "response": response,
        "conclusion": "通过（未被注入）" if passed else "★异常：疑似注入生效",
        "note": note,
    })
    print("[%s-%s] %-22s %s" % (group, payload_id, payload[:22], "载荷被当普通字符串处理" if passed else "★疑似注入生效"))


def main():
    before = snapshot()
    print("执行前库内基线：%s\n" % json.dumps(before, ensure_ascii=False))

    # ★ /api/rooms 在第 10 周 PR#4 之后要求登录（令牌鉴权），不带令牌只会拿到 401，
    #   根本走不到 SQL —— 那样测的不是注入，是鉴权。所以先登录。
    user_token = login("user1")

    # ---------------- 第 1 组：登录账号名 ----------------
    for pid, payload in PAYLOADS:
        body = {"username": payload, "password": "whatever"}
        st, resp = call("POST", "/auth/login", body)
        token = (resp.get("data") or {}).get("token") if isinstance(resp.get("data"), dict) else None
        ok = st is not None and st < 500 and token is None and resp.get("code") in (400, 401)
        add("G1-登录账号名", pid, payload,
            {"method": "POST", "url": BASE + "/auth/login", "body": body},
            {"httpStatus": st, "body": resp}, ok,
            "未返回令牌；服务未报 5xx" if ok else "返回了令牌或 5xx，需立即排查")

    # ---------------- 第 2 组：会议室名称／位置筛选 ----------------
    for pid, payload in PAYLOADS:
        q = urllib.parse.quote(payload, safe="")
        path = "/rooms?status=ENABLED&location=" + q
        st, resp = call("GET", path, token=user_token)
        data = resp.get("data")
        n = len(data) if isinstance(data, list) else -1
        # 期望：返回 0 条（无任何会议室的名字/位置含该串），且不是 5xx
        ok = st is not None and st < 500 and resp.get("code") == 200 and n == 0
        add("G2-会议室位置筛选", pid, payload,
            {"method": "GET", "url": BASE + path},
            {"httpStatus": st, "resultCount": n, "body": resp}, ok,
            "返回 %d 条（应为 0：没有任何会议室名称含该串）" % n)

    # ---------------- 第 3 组：预约主题（写入后原样回读） ----------------
    token = user_token
    date = (datetime.now() + timedelta(days=60)).strftime("%Y-%m-%d")
    # 幂等：清掉上一轮同载荷的写入，保证"一次执行＝一组新数据"，库内条数可对齐
    for _, payload in PAYLOADS:
        db_scalar("delete from reservation where topic = '%s'" % payload.replace("'", "''"))
    for idx, (pid, payload) in enumerate(PAYLOADS):
        start = "%02d:00:00" % (8 + idx)
        end = "%02d:00:00" % (9 + idx)
        body = {"roomId": 1, "date": date,
                "startTime": "%s %s" % (date, start), "endTime": "%s %s" % (date, end),
                "topic": payload, "attendeeCount": 3, "contact": "虞业宸"}
        st, resp = call("POST", "/bookings", body, token)
        stored = None
        if st == 200 and isinstance(resp.get("data"), dict):
            rid = resp["data"].get("id")
            stored = db_scalar("select topic from reservation where id=%s" % rid)
        same = stored is not None and stored.replace("\\", "") == payload.strip().replace("\\", "")
        ok = st is not None and st < 500 and (st == 200 and same)
        add("G3-预约主题", pid, payload,
            {"method": "POST", "url": BASE + "/bookings", "body": body},
            {"httpStatus": st, "body": resp, "dbStoredTopic": stored}, ok,
            "库内原样存储，未被执行" if ok else "库内存储不一致或请求失败")

    # ---------------- 第 4 组：管理员列表筛选（status 白名单入参） ----------------
    admin_token = login("admin")
    for pid, payload in PAYLOADS:
        q = urllib.parse.quote(payload, safe="")
        path = "/admin/bookings?status=" + q + "&pageNum=1&pageSize=5"
        st, resp = call("GET", path, token=admin_token)
        code = resp.get("code")
        data = resp.get("data")
        items = (data or {}).get("items") if isinstance(data, dict) else None
        n = len(items) if isinstance(items, list) else 0
        # 期望：状态走枚举白名单 → 400 提示；即使 200 也不许把全表数据吐出来
        ok = st is not None and st < 500 and (code == 400 or (code == 200 and n == 0))
        add("G4-管理端状态筛选", pid, payload,
            {"method": "GET", "url": BASE + path,
             "headers": {"Authorization": "Bearer <admin token>"}},
            {"httpStatus": st, "code": code, "returnedItems": n, "body": resp}, ok,
            "状态入参走枚举校验，未进入 SQL 拼接")

    after = snapshot()
    print("\n执行后库内基线：%s" % json.dumps(after, ensure_ascii=False))
    intact = (before["reservationTableExists"] == after["reservationTableExists"] == "1"
              and before["userRows"] == after["userRows"]
              and before["roomRows"] == after["roomRows"])
    print("表结构与关键表数据：%s（reservation 行数 %s → %s，新增行为本组写入的载荷主题）"
          % ("完好" if intact else "★被破坏", before["reservationRows"], after["reservationRows"]))

    # P4 的 DROP TABLE 载荷若真被执行，reservation 表会消失 —— 单独显式核对
    exists_now = db_scalar("select count(*) from information_schema.tables "
                           "where table_schema='meeting_room' and table_name='reservation'")
    print("reservation 表是否仍在：%s" % ("在" if exists_now == "1" else "★不在"))

    passed_cnt = sum(1 for e in EVIDENCE if e["conclusion"].startswith("通过"))
    print("\n合计 %d 组载荷，%d 组未被注入" % (len(EVIDENCE), passed_cnt))

    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "injection_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"executedAt": datetime.now().isoformat(timespec="seconds"),
                   "baseUrl": BASE, "before": before, "after": after,
                   "reservationTableStillExists": exists_now == "1",
                   "intact": intact, "total": len(EVIDENCE), "safe": passed_cnt,
                   "evidence": EVIDENCE}, f, ensure_ascii=False, indent=2)
    print("三样证据已写入 %s" % out)
    return 0 if (passed_cnt == len(EVIDENCE) and intact and exists_now == "1") else 1


if __name__ == "__main__":
    sys.exit(main())
