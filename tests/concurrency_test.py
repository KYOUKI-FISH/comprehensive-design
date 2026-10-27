# -*- coding: utf-8 -*-
"""并发防超卖自测（第 9 周提前验；第 10 周要放大到 50 并发）。

★ 为什么第 9 周就要做：
  第 11 周的硬指标是"50 个并发抢同一间会议室同一时段，只允许 1 条成功、49 条明确失败"。
  如果行锁方案有问题，第 11 周才发现就来不及了——所以第 9 周先做 10 并发（廉价替身）验一遍。

★ 关键：必须【同时发起】。顺序发等于串行，等于没测超卖。
  本脚本用 threading.Barrier 让所有线程在同一时刻冲过闸门。

用法：
    "<venv>/bin/python" .workbuddy/tools/week09_concurrency.py [并发数]
"""
import json
import os
import sys
import threading
import urllib.request
import urllib.error
from datetime import datetime, timedelta

# ★ 必须绕过本机代理：沙箱环境设了 HTTP_PROXY/HTTPS_PROXY 指向 127.0.0.1，
#   urllib 会走代理，代理转发不到本机后端 → 报 "HTTP 502"。
for _k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    if _k in os.environ:
        del os.environ[_k]
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = "http://localhost:8080/api"
CONCURRENCY = int(sys.argv[1]) if len(sys.argv) > 1 else 10

# 用一个空闲的未来时段（种子数据占用 09:00-12:00 的 A101，这里用 20:00-21:00）
target = datetime.now() + timedelta(days=30)
DATE = target.strftime("%Y-%m-%d")
START = target.strftime("%Y-%m-%d 20:00")
END = target.strftime("%Y-%m-%d 21:00")
ROOM_ID = 1


def call(method, path, body=None, token=None):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            return payload.get("code"), payload.get("message"), payload.get("data")
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8"))
            return payload.get("code"), payload.get("message"), payload.get("data")
        except Exception:
            return None, "HTTP %s" % e.code, None
    except Exception as e:
        return None, str(e), None


# 登录拿令牌
code, msg, data = call("POST", "/auth/login", {"username": "user1", "password": "123456"})
if code != 200:
    print("登录失败：%s %s" % (code, msg))
    raise SystemExit(1)
token = data["token"]

results = [None] * CONCURRENCY
barrier = threading.Barrier(CONCURRENCY)


def worker(index):
    """所有线程在 barrier 处等待，一起冲出去——这是本脚本的关键。"""
    payload = {
        "roomId": ROOM_ID,
        "date": DATE,
        "startTime": START,
        "endTime": END,
        "topic": "并发自测-%d" % index,
        "attendeeCount": 3,
        "contact": "并发测试"
    }
    barrier.wait()  # ★ 同一秒（同一时刻）发起
    results[index] = call("POST", "/bookings", payload, token)


print("=" * 72)
print("并发防超卖自测：%d 个并发抢同一间会议室同一时段" % CONCURRENCY)
print("目标：会议室 %d，%s %s — %s" % (ROOM_ID, DATE, "20:00", "21:00"))
print("=" * 72)

threads = [threading.Thread(target=worker, args=(i,)) for i in range(CONCURRENCY)]
for t in threads:
    t.start()
for t in threads:
    t.join()

success = [r for r in results if r and r[0] == 200]
conflict = [r for r in results if r and r[0] == 409]
other = [(i, r) for i, r in enumerate(results) if r and r[0] not in (200, 409)]
server_error = [r for r in results if r and r[0] in (500, None)]

print()
print("成功（200）      ：%d 条" % len(success))
for r in success:
    print("   预约号 %s" % ((r[2] or {}).get("reservationNo")))
print("冲突（409）      ：%d 条" % len(conflict))
if conflict:
    print("   提示：%s" % conflict[0][1])
    slots = (conflict[0][2] or {}).get("occupiedSlots") if conflict[0][2] else None
    if slots:
        print("   返回的被占用时段：%s" % [("%s-%s" % (s["startTime"][11:16], s["endTime"][11:16])) for s in slots])
print("其他（应为 0）   ：%d 条" % len(other))
for i, r in other:
    print("   #%d code=%s message=%s" % (i, r[0], r[1]))

# ★ 查库核对：有效预约必须只有 1 条
print()
print("--- 库内核对 ---")
import subprocess
check = subprocess.run(
    ["mysql", "-u", "root", "-p123456", "-N", "-B", "-e",
     "USE meeting_room; SELECT COUNT(*) FROM reservation WHERE room_id=%d "
     "AND reserve_date='%s' AND start_time='%s' AND status<>'CANCELLED';"
     % (ROOM_ID, DATE, START)],
    capture_output=True, text=True
)
db_count = check.stdout.strip() if check.stdout else "?"
print("该时段库内有效预约条数：%s" % db_count)

# 编号唯一性
check2 = subprocess.run(
    ["mysql", "-u", "root", "-p123456", "-N", "-B", "-e",
     "USE meeting_room; SELECT COUNT(DISTINCT reservation_no), COUNT(*) FROM reservation "
     "WHERE reserve_date='%s';" % DATE],
    capture_output=True, text=True
)
print("该日期预约编号：去重数 / 总数 = %s" % (check2.stdout.strip() if check2.stdout else "?"))

print()
print("=" * 72)
ok = len(success) == 1 and len(conflict) == CONCURRENCY - 1 and db_count == "1"
if ok:
    print("判定：★ 通过 —— 只允许 1 条成功，其余 %d 条明确失败，库内也只有 1 条" % len(conflict))
elif len(success) == 0:
    print("判定：★ 失败 —— 0 条成功（连一条都没成，说明行锁把正常请求也挡了）")
else:
    print("判定：★ 失败 —— %d 条成功，超卖！" % len(success))
print("=" * 72)
