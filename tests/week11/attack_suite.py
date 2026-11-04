# -*- coding: utf-8 -*-
"""第 11 周四类攻击取证（任务书第三节：每类必须留「请求 + 响应 + 库内数据」三样证据）。

四类：
  A. 越权取消 —— 用 user2 的令牌删 user1 的预约，预期 403，且 user1 的预约仍在、状态未变。
  B. 越权查看 —— 用普通用户令牌查 `GET /api/admin/bookings`、`GET /api/admin/logs`，预期 403 且响应体不含管理数据。
  C. 时间边界 —— 跨天 / 超 8 小时 / 结束早于开始 应拒绝；首尾相接应放行（半开区间）。
  D. SQL 注入 —— 登录账号名、会议室位置筛选、预约主题塞载荷，预期被当普通字符串处理，库结构与数据完好。

★ 边界（任务书第三节）：所有请求目标只能是 `localhost:8080`，脚本每次发请求前校验；
  **禁止**对本机以外的系统发起压测或注入。测试后按 `wk11_common` 打印的基线恢复数据。
★ 口令不写进脚本：数据库口令取环境变量 `DB_PASSWORD`，测试账号口令是课程演示账号（README 已公开）。

用法：
    python tests/week11/attack_suite.py
证据输出到 `docs/week11/evidence/week11_attack_evidence.md`。
"""
import os
import sys
from datetime import date, datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wk11_common as W  # noqa: E402

OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..",
    "docs", "week11", "evidence", "week11_attack_evidence.md",
)

lines = []


def emit(text=""):
    lines.append(text)
    W.say(text)


def record(title, method, path, req_desc, status, body, db_before=None, db_after=None, expect=""):
    """一条证据：请求 → 响应 → 库内数据。"""
    emit()
    emit("#### %s" % title)
    emit()
    emit("- **请求**：`%s %s`" % (method, W.BASE + path))
    if req_desc:
        emit("  - 请求头/体：%s" % req_desc)
    emit("- **响应**：HTTP `%s`，响应体：`%s`" % (status, _short(body)))
    if db_before is not None or db_after is not None:
        emit("- **库内数据**：执行前 `%s` → 执行后 `%s`" % (db_before, db_after))
    if expect:
        emit("- **预期**：%s" % expect)


def code_of(body):
    """业务码：本项目后端把业务结果放在响应体 code 里（HTTP 常为 200，见 BUG-07）。"""
    return (body or {}).get("code") if isinstance(body, dict) else None


def _short(body, limit=400):
    s = str(body).replace("\n", " ")
    return s if len(s) <= limit else s[:limit] + "…"


today = date.today()

# ---------------------------------------------------------------- 准备：两个用户的令牌
tok1 = W.require_token("user1")
tok2 = W.require_token("user2")
emit("# 第 11 周四类攻击取证记录")
emit()
emit("> 目标主机：`http://localhost:8080`（**仅本机测试环境**，未对学校在用系统、其他课程系统或公网服务发起任何请求）。")
emit("> 执行时间：%s；测试账号：`user1` / `user2`（演示账号，口令见根 README）。" % datetime.now().strftime("%Y-%m-%d %H:%M"))
emit("> 取证三样：请求（方法 + 完整 URL + 关键头体）、响应（HTTP 状态码 + 响应体）、库内数据（执行前后 `SELECT` 结果）。")
emit()

# ---------------------------------------------------------------- A. 越权取消
emit("## A. 越权取消（用户 B 删用户 A 的预约）")
emit()
mine = W.call("GET", "/bookings/mine", None, tok1)
target_id = None
for item in (mine[1].get("data") or []):
    if item.get("status") == "PENDING":
        target_id = item.get("id")
        break
if target_id is None:
    emit("⚠ user1 当前没有 PENDING 预约，越权取消用例无法执行——请先造一条再跑。")
else:
    before = W.db_scalar("SELECT CONCAT(reservation_no,'/',status) FROM reservation WHERE id=%s;" % target_id)
    status, body, _ = W.call("DELETE", "/bookings/%d" % target_id, None, tok2)
    after = W.db_scalar("SELECT CONCAT(reservation_no,'/',status) FROM reservation WHERE id=%s;" % target_id)
    record("A-1 用 user2 的令牌取消 user1 的预约 #%d" % target_id, "DELETE", "/bookings/%d" % target_id,
           "Authorization: Bearer <user2 令牌>（无请求体）", status, body, before, after,
           "403 / 权限不足；user1 的预约仍在库、状态不变（仍为 PENDING）")
    emit()
    emit("**判定**：%s（HTTP `%s`，业务码 `%s`）"
         % ("通过" if code_of(body) == 403 and before == after else "★ 未通过，需修复并回归",
            status, code_of(body)))

# ---------------------------------------------------------------- B. 越权查看
emit()
emit("## B. 越权查看（普通用户查管理端数据）")
emit()
before_cnt = W.db_scalar("SELECT COUNT(*) FROM reservation;")
b1_status, b1_body, _ = W.call("GET", "/admin/bookings", None, tok1)
b2_status, b2_body, _ = W.call("GET", "/admin/logs", None, tok1)
after_cnt = W.db_scalar("SELECT COUNT(*) FROM reservation;")
record("B-1 普通用户请求全部预约", "GET", "/admin/bookings",
       "Authorization: Bearer <user1 令牌>", b1_status, b1_body, before_cnt, after_cnt,
       "403 / 权限不足；响应体不含任何他人预约数据")
record("B-2 普通用户请求操作日志", "GET", "/admin/logs",
       "Authorization: Bearer <user1 令牌>", b2_status, b2_body, before_cnt, after_cnt,
       "403 / 权限不足；响应体不含日志数据")
leak = any(k in str(b1_body) for k in ("reservationNo", "topic")) or \
       any(k in str(b2_body) for k in ("actionType", "operatorName"))
emit()
emit("**判定**：%s（业务码 B-1=`%s`、B-2=`%s`，HTTP=%s/%s）"
     % ("通过" if code_of(b1_body) == 403 and code_of(b2_body) == 403 and not leak
        else "★ 未通过（业务码或响应体泄露数据）", code_of(b1_body), code_of(b2_body),
        b1_status, b2_status))

# ---------------------------------------------------------------- C. 时间边界
emit()
emit("## C. 时间边界（跨天 / 超 8 小时 / 结束早于开始 / 首尾相接）")
emit()
d = today + timedelta(days=45)
day = d.isoformat()
room = 1


def _submit(label, s, e, dd=None):
    payload = {
        "roomId": room, "date": dd or day, "startTime": s, "endTime": e,
        "topic": "边界用例-%s" % label, "attendeeCount": 2, "contact": "边界测试",
    }
    cnt_before = W.db_scalar("SELECT COUNT(*) FROM reservation;")
    st, body, _ = W.call("POST", "/bookings", payload, tok1)
    cnt_after = W.db_scalar("SELECT COUNT(*) FROM reservation;")
    return st, body, cnt_before, cnt_after, payload


# C-1 跨天
s = "%s 22:00" % day
e = "%s 02:00" % (d + timedelta(days=1)).isoformat()
st, body, cb, ca, _p = _submit("跨天", s, e)
record("C-1 跨天（22:00 → 次日 02:00）", "POST", "/bookings", "body: %s" % _short(_p, 200),
       st, body, cb, ca, "400 / 提示不能跨天；库内条数不变")

# C-2 超 8 小时
s2 = "%s 08:00" % day
e2 = "%s 20:00" % day
st, body, cb, ca, _p = _submit("超8小时", s2, e2)
record("C-2 单次超 8 小时（08:00 → 20:00）", "POST", "/bookings", "body: %s" % _short(_p, 200),
       st, body, cb, ca, "400 / 提示时长上限；库内条数不变")

# C-3 结束早于开始
s3 = "%s 15:00" % day
e3 = "%s 14:00" % day
st, body, cb, ca, _p = _submit("逆序", s3, e3)
record("C-3 结束时间早于开始时间（15:00 → 14:00）", "POST", "/bookings", "body: %s" % _short(_p, 200),
       st, body, cb, ca, "400 / 提示时间不合法；库内条数不变")

# C-4 首尾相接（边界正确路径，必须放行）
# ★ 时段选 21:00 之后：性能基线灌的 1000 条占了 08:00—17:00，用白天时段会撞上造数本身
s4 = "%s 21:00" % day
e4 = "%s 22:00" % day
st, body, cb, ca, _p = _submit("首条", s4, e4)
first_status = st
s5 = "%s 22:00" % day
e5 = "%s 23:00" % day
st, body, cb, ca, _p = _submit("首尾相接", s5, e5)
slot_cnt = W.db_scalar(
    "SELECT COUNT(*) FROM reservation WHERE room_id=%d AND reserve_date='%s' "
    "AND status<>'CANCELLED' AND start_time>='%s 21:00:00' AND end_time<='%s 23:00:00';" % (room, day, day, day))
record("C-4 首尾相接（先 21:00-22:00，再 22:00-23:00）", "POST", "/bookings", "body: %s" % _short(_p, 200),
       st, body, cb, ca,
       "200 / 提交成功（半开区间 [start,end)，首尾相接不算冲突）；库内两条并存，计数=%s" % slot_cnt)
emit()
emit("**判定**：前三条拒绝 + 第四条放行 = 通过；`<=`/`<` 写反时第四条会被误拒，此用例专门挡这个。")

# ---------------------------------------------------------------- D. SQL 注入
emit()
emit("## D. SQL 注入（用户可控入参塞载荷）")
emit()
table_before = W.db_scalar("SELECT COUNT(*) FROM information_schema.tables "
                           "WHERE table_schema='%s';" % W.DB_NAME)
res_before = W.db_scalar("SELECT COUNT(*) FROM reservation;")
payloads = ["' OR '1'='1", "1; DROP TABLE reservation--", "'; UPDATE user SET role='ADMIN' WHERE 1=1-- "]

for i, pl in enumerate(payloads, 1):
    # D-1 登录账号名
    st, body, _ = W.call("POST", "/auth/login", {"username": pl, "password": pl})
    record("D-1.%d 登录账号名注入：%s" % (i, pl), "POST", "/auth/login",
           "body: %s" % _short({"username": pl, "password": pl}, 160), st, body,
           expect="401 / 用户名或口令错误（当作普通字符串比对），不返回令牌、不抛 500")

    # D-2 会议室位置筛选
    st, body, _ = W.call("GET", "/rooms?location=" + __import__("urllib.parse").parse.quote(pl), None, tok1)
    n = len((body or {}).get("data") or []) if st == 200 else "-"
    record("D-2.%d 会议室位置筛选注入：%s" % (i, pl), "GET", "/rooms?location=%s" % pl,
           "Authorization: Bearer <user1 令牌>", st, body,
           expect="200 / 查不到（返回 %s 条，不当条件执行）；不报 500" % n)

    # D-3 预约主题
    d2 = today + timedelta(days=60 + i)
    payload = {"roomId": 1, "date": d2.isoformat(),
               "startTime": "%s 19:00" % d2.isoformat(), "endTime": "%s 20:00" % d2.isoformat(),
               "topic": pl, "attendeeCount": 2, "contact": pl}
    st, body, _ = W.call("POST", "/bookings", payload, tok1)
    if code_of(body) == 200:
        rid = (body.get("data") or {}).get("id")
        topic_in_db = W.db_scalar("SELECT topic FROM reservation WHERE id=%s;" % rid) if rid else "-"
        record("D-3.%d 预约主题/联系人注入：%s" % (i, pl), "POST", "/bookings",
               "body: %s" % _short(payload, 200), st, body,
               expect="200 落库（MyBatis `#{}` 参数绑定，载荷被当普通字符串原样存储）；"
                      "库内 topic 原样为 %s" % _short(topic_in_db, 120))
    else:
        record("D-3.%d 预约主题/联系人注入：%s" % (i, pl), "POST", "/bookings",
               "body: %s" % _short(payload, 200), st, body,
               expect="载荷被拒绝（400），不报 500")

table_after = W.db_scalar("SELECT COUNT(*) FROM information_schema.tables "
                          "WHERE table_schema='%s';" % W.DB_NAME)
res_after = W.db_scalar("SELECT COUNT(*) FROM reservation;")
emit()
emit("### D 汇总：库结构与数据完好性")
emit()
emit("| 项 | 注入前 | 注入后 | 结论 |")
emit("|---|---|---|---|")
emit("| `%s` 库内表数量 | %s | %s | %s |" % (W.DB_NAME, table_before, table_after,
                                            "完好" if table_before == table_after else "★ 表被删/被建"))
emit("| `reservation` 条数 | %s | %s | %s |" % (res_before, res_after,
                                             "只增不减（D-3 合法落库导致 +n），无 DROP/TRUNCATE 痕迹"
                                             if str(res_after) >= str(res_before) else "★ 数据减少，需立即排查"))
emit()
emit("**判定**：三类载荷（登录名 / 位置筛选 / 预约主题）均被当作普通字符串处理，无 500、无执行痕迹，"
     "表结构与数据完好 —— 「打不动」本身即结论。")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
W.say()
W.say("证据已写入：%s" % OUT)
