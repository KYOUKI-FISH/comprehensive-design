# -*- coding: utf-8 -*-
"""第 9 周联调自测脚本：五条接口逐条真打，输出可粘进联调记录的结果。

用法：
    "<venv>/bin/python" .workbuddy/tools/week09_smoke.py

覆盖任务书表 1 的五行：
    1. POST /api/auth/login          登录页
    2. GET  /api/rooms               会议室列表页
    3. GET  /api/rooms + /occupancy  会议室详情页（D1 组合供数）
    4. POST /api/bookings            预约表单页
    5. GET  /api/bookings/mine       我的预约页（第 10 周做，本脚本应显示"未开始"）

额外验的（不占表 1 行）：鉴权拦截、越权管理接口被拒、停用会议室不可预约、
半开区间首尾相接不冲突、必填校验、409 带占用时段。
"""
import json
import os
import urllib.request
import urllib.error
from datetime import datetime, timedelta

# ★ 必须绕过本机代理：沙箱环境设了 HTTP_PROXY/HTTPS_PROXY 指向 127.0.0.1，
#   urllib 会走代理，代理转发不到本机后端 → 报 "HTTP 502"。
#   （curl 同理，命令行要加 --noproxy '*'）
for _k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    if _k in os.environ:
        del os.environ[_k]
_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
urllib.request.install_opener(_opener)

BASE = "http://localhost:8080/api"

# 种子数据的预约都落在 2026-11-02（见 schema.sql 初始化数据），用同一天测
TEST_DATE = "2026-11-02"


def call(method, path, body=None, token=None):
    """打一次接口，返回 (code, message, data, http_status)。"""
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            return payload.get("code"), payload.get("message"), payload.get("data"), resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            payload = json.loads(raw)
            return payload.get("code"), payload.get("message"), payload.get("data"), e.code
        except Exception:
            return None, raw, None, e.code
    except Exception as e:
        return None, "连接失败：" + str(e), None, 0


def fmt_slot(slot):
    return "%s-%s" % (slot["startTime"][11:16], slot["endTime"][11:16])


results = []


def record(name, ok, detail):
    results.append((name, "通过" if ok else "失败", detail))
    print("[%s] %s —— %s" % ("通过" if ok else "失败", name, detail))


print("=" * 72)
print("第 9 周联调自测（任务书表 1 五行 ＋ 额外校验）")
print("=" * 72)

# ---------- 行 1：登录 ----------
code, msg, data, http = call("POST", "/auth/login", {"username": "user1", "password": "123456"})
ok = code == 200 and data and data.get("token")
record("POST /api/auth/login  登录页", bool(ok),
       "返回 code=%s，角色=%s，令牌长度=%s" % (
           code, (data or {}).get("role"), len((data or {}).get("token") or "")))
token = (data or {}).get("token")

# 登录失败：密码错误
code2, msg2, _, _ = call("POST", "/auth/login", {"username": "user1", "password": "wrong"})
record("登录失败分支（密码错误）", code2 == 401 and msg2 == "账号或密码错误",
       "code=%s message=%s（账号不存在与密码错误同提示，防枚举）" % (code2, msg2))

if not token:
    print("\n登录失败，后续测试无法进行")
    raise SystemExit(1)

# ---------- 行 2：会议室列表（需带令牌，第 9 周起业务接口一律鉴权）----------
code, msg, data, http = call("GET", "/rooms?status=ENABLED&date=" + TEST_DATE, token=token)
ok = code == 200 and isinstance(data, list)
record("GET /api/rooms  会议室列表页", bool(ok),
       "code=%s 返回 %d 间（status=ENABLED）" % (code, len(data) if isinstance(data, list) else -1))
room_list = data if isinstance(data, list) else []

# 按名称筛选
code, msg, data, _ = call("GET", "/rooms?location=%E9%80%B8%E5%A4%AB%E6%A5%BC&status=ENABLED", token=token)
record("  └ 筛选（位置关键字=逸夫楼）", code == 200 and len(data) == 2,
       "筛出 %d 间：%s" % (len(data) if isinstance(data, list) else -1,
                        [r["name"] for r in (data or [])]))

# 按容量筛选
code, msg, data, _ = call("GET", "/rooms?minCapacity=15&status=ENABLED", token=token)
record("  └ 筛选（最小容量=15）", code == 200 and len(data) == 1,
       "筛出 %d 间：%s" % (len(data) if isinstance(data, list) else -1,
                        [r["name"] for r in (data or [])]))

# 无结果
code, msg, data, _ = call("GET", "/rooms?location=%E4%B8%8D%E5%AD%98%E5%9C%A8", token=token)
record("  └ 无匹配结果", code == 200 and data == [],
       "code=%s 返回空数组（不报错，FR-05）" % code)

# 停用会议室（单独查 status=DISABLED 才能拿到，列表页固定只传 ENABLED）
code, msg, disabled_list, _ = call("GET", "/rooms?status=DISABLED", token=token)
disabled = (disabled_list or [None])[0]
code, msg, data, _ = call("GET", "/rooms?status=ENABLED", token=token)
names = [r["name"] for r in (data or [])]
record("  └ 停用会议室被排除（FR-06）",
       "图书馆B301报告厅" not in names and disabled is not None,
       "启用列表 %s 中不含停用会议室；停用会议室 %s 仍可通过 status=DISABLED 查到" % (
           names, (disabled or {}).get("name")))

room_id = room_list[0]["id"] if room_list else 1
room_name = room_list[0]["name"] if room_list else "?"

# ---------- 行 3：详情页（D1 组合供数） ----------
code, msg, data, _ = call("GET", "/rooms?date=" + TEST_DATE, token=token)
basic = next((r for r in (data or []) if r["id"] == room_id), None)
record("GET /api/rooms（详情页基本信息来源）", code == 200 and basic is not None,
       "取得 %s：位置=%s 容量=%s 状态=%s" % (
           (basic or {}).get("name"), (basic or {}).get("location"),
           (basic or {}).get("capacity"), (basic or {}).get("status")))

code, msg, data, _ = call("GET", "/rooms/%d/occupancy?date=%s" % (room_id, TEST_DATE), token=token)
slots = (data or {}).get("occupiedSlots", [])
record("GET /api/rooms/%d/occupancy  会议室详情页" % room_id, code == 200 and data is not None,
       "code=%s 已占用时段 %d 段：%s" % (code, len(slots), [fmt_slot(s) for s in slots]))

# 不存在的会议室
code, msg, data, _ = call("GET", "/rooms/99999/occupancy?date=" + TEST_DATE, token=token)
record("  └ 会议室不存在", code == 404 and msg == "会议室不存在",
       "code=%s message=%s" % (code, msg))

# 缺 date
code, msg, data, _ = call("GET", "/rooms/%d/occupancy" % room_id, token=token)
record("  └ 缺 date 参数", code == 400 and msg == "请选择预约日期",
       "code=%s message=%s" % (code, msg))

# ---------- 行 4：提交预约 ----------
# 找一个空闲时段（种子数据占用了 09:00-12:00 的 A101，用 14:00-15:00）
new_booking = {
    "roomId": room_id,
    "date": TEST_DATE,
    "startTime": "%s 14:00" % TEST_DATE,
    "endTime": "%s 15:00" % TEST_DATE,
    "topic": "第9周联调自测会议",
    "attendeeCount": 5,
    "contact": "联调测试员",
    "purpose": "第9周最小原型联调"
}
code, msg, data, _ = call("POST", "/bookings", new_booking, token)
ok = code == 200 and data and data.get("reservationNo")
record("POST /api/bookings  预约表单页", bool(ok),
       "code=%s 预约号=%s 状态=%s" % (code, (data or {}).get("reservationNo"),
                                (data or {}).get("status")))
created_no = (data or {}).get("reservationNo")

# 重叠 → 409 + 被占用时段（FR-10）
overlap = dict(new_booking)
overlap["startTime"] = "%s 14:30" % TEST_DATE
overlap["endTime"] = "%s 15:30" % TEST_DATE
code, msg, data, _ = call("POST", "/bookings", overlap, token)
conflict_slots = (data or {}).get("occupiedSlots", []) if data else []
record("  └ 重叠提交 → 409 且带占用时段（FR-10）",
       code == 409 and msg == "该会议室在此时间段已被占用" and len(conflict_slots) > 0,
       "code=%s message=%s 占用时段=%s" % (code, msg, [fmt_slot(s) for s in conflict_slots]))

# ★ 首尾相接 → 必须成功（半开区间，第 12 周预答辩重点抽这条）
adjacent = dict(new_booking)
adjacent["startTime"] = "%s 15:00" % TEST_DATE
adjacent["endTime"] = "%s 16:00" % TEST_DATE
adjacent["topic"] = "首尾相接验证"
code, msg, data, _ = call("POST", "/bookings", adjacent, token)
record("  └ ★ 首尾相接（15:00 接 15:00）→ 必须成功", code == 200,
       "code=%s message=%s 预约号=%s（判定式用 < 而非 <=，首尾相接不算冲突）" % (
           code, msg, (data or {}).get("reservationNo")))

# 缺必填
code, msg, data, _ = call("POST", "/bookings",
                          dict(new_booking, topic="", startTime="%s 17:00" % TEST_DATE,
                               endTime="%s 18:00" % TEST_DATE), token)
record("  └ 缺会议主题 → 400 中文提示", code == 400 and "会议主题" in (msg or ""),
       "code=%s message=%s" % (code, msg))

# 结束早于开始
code, msg, data, _ = call("POST", "/bookings",
                          dict(new_booking, startTime="%s 16:00" % TEST_DATE,
                               endTime="%s 15:00" % TEST_DATE), token)
record("  └ 结束早于开始 → 400（FR-08）", code == 400 and "晚于" in (msg or ""),
       "code=%s message=%s" % (code, msg))

# 超 8 小时
code, msg, data, _ = call("POST", "/bookings",
                          dict(new_booking, startTime="%s 08:00" % TEST_DATE,
                               endTime="%s 17:00" % TEST_DATE), token)
record("  └ 超 8 小时 → 400（FR-08）", code == 400 and "8 小时" in (msg or ""),
       "code=%s message=%s" % (code, msg))

# 停用会议室不可预约（FR-06）
if disabled:
    code, msg, data, _ = call("POST", "/bookings",
                              dict(new_booking, roomId=disabled["id"],
                                   startTime="%s 19:00" % TEST_DATE,
                                   endTime="%s 20:00" % TEST_DATE), token)
    record("  └ 停用会议室不可预约（FR-06）", code == 400 and "停用" in (msg or ""),
           "code=%s message=%s" % (code, msg))

# ---------- 行 5：我的预约（第 10 周做） ----------
code, msg, data, _ = call("GET", "/bookings/mine", token=token)
record("GET /api/bookings/mine  我的预约页", code == 404,
       "code=%s —— 接口尚未实现，属第 10 周（任务书表 1 末行标注「未开始」）" % code)

# ---------- 额外：鉴权与越权 ----------
code, msg, data, _ = call("GET", "/rooms")
record("无令牌访问业务接口 → 401", code == 401,
       "code=%s message=%s" % (code, msg))

code, msg, data, _ = call("GET", "/admin/bookings", token=token)
record("普通用户调管理接口 → 403（NF-03）", code == 403,
       "code=%s message=%s" % (code, msg))

code, msg, data, _ = call("GET", "/health")
record("健康检查 /api/health 公开可访问", code == 200,
       "code=%s database=%s" % (code, (data or {}).get("database")))

# 登出后旧令牌失效
code, msg, data, _ = call("POST", "/auth/logout", token=token)
record("登出接口可用", code == 200, "code=%s" % code)
code, msg, data, _ = call("GET", "/rooms", token=token)
record("  └ 登出后旧令牌失效（NF-04）", code == 401,
       "code=%s message=%s" % (code, msg))

print()
print("=" * 72)
passed = sum(1 for r in results if r[1] == "通过")
print("总计 %d 项，通过 %d 项，失败 %d 项" % (len(results), passed, len(results) - passed))
print("=" * 72)
if passed != len(results):
    print("\n失败项：")
    for name, status, detail in results:
        if status != "通过":
            print("  - %s：%s" % (name, detail))
