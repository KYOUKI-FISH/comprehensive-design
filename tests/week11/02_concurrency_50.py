# -*- coding: utf-8 -*-
"""第 11 周 并发超卖测试：50 个请求【同一秒】抢同一房间同一时段（虞业宸）。

★ 三条铁律（任务书第三节，做不到按未做计）：
  1. 并发数 = 50，不许减成 5 个；
  2. 必须【同一秒发起】，不许顺序发（顺序发等于串行，测不出超卖）；
  3. 必须真实发出请求并留存响应（本脚本把 50 条响应全量落盘）。

★ 怎么保证"同一秒"：
  threading.Barrier(50) 让 50 个线程全部就绪后，由主线程序号枪响——
  所有线程在【同一瞬间】冲过闸门再发 HTTP。脚本会输出 50 个线程的起跑时间戳
  与跨度（毫秒），跨度必须 < 1000ms，否则不算"同一秒"。

★ 判据（与 docs/week11/周推进计划_第11周.md §2.1.2.1 一致）：
  - 恰好 1 条成功（code=200）；
  - 其余 49 条是【明确失败】= 409 ＋ 冲突提示，不是 500／超时／空响应；
  - 50 条响应里无唯一键冲突（uk_reservation_no / Duplicate entry）报错；
  - 库内该时段有效预约条数 = 1（脚本外由 SQL 核对，见记录文档）。

用法：
    python tests/week11/02_concurrency_50.py [并发数]     # 默认 50
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

for _k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    os.environ.pop(_k, None)
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = os.environ.get("BASE_URL", "http://localhost:8080/api")
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
N = int(sys.argv[1]) if len(sys.argv) > 1 else 50

# 干净的未来日期 + 晚间空闲时段，避开第 9/10 周遗留数据
# ★ 偏移量可用环境变量改：先跑小规模复验（10 并发）再跑正式 50 并发时，
#   两次必须落在【不同日期】，否则第一次留下的那 1 条会把第二次全判成冲突。
TARGET = datetime.now() + timedelta(days=int(os.environ.get("W11_DAY_OFFSET", "50")))
DATE = TARGET.strftime("%Y-%m-%d")
START = DATE + " 20:00:00"
END = DATE + " 21:00:00"
ROOM_ID = 2
TOPIC = "W11-CONC-%d" % N


def call(method, path, body=None, token=None, timeout=60):
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
    except Exception as e:
        return None, {"error": str(e)}


def login(username, password="123456"):
    st, payload = call("POST", "/auth/login", {"username": username, "password": password})
    assert st == 200 and payload.get("code") == 200, "登录失败 %s: %s" % (username, payload)
    return payload["data"]["token"]


def main():
    # 库内只有 3 个账号，50 个请求由 user1 / user2 交替携带 —— 记录里如实写明
    t1 = login("user1")
    t2 = login("user2")
    tokens = [t1, t2]

    barrier = threading.Barrier(N)
    results = [None] * N
    marks = [0.0] * N

    def worker(i):
        body = {
            "roomId": ROOM_ID, "date": DATE,
            "startTime": START, "endTime": END,
            "topic": TOPIC, "attendeeCount": 6, "contact": "虞业宸",
        }
        barrier.wait()                      # ★ 50 个线程在这里对齐，主线号令枪响
        marks[i] = time.time()
        st, payload = call("POST", "/bookings", body, tokens[i % 2])
        results[i] = {
            "index": i,
            "firedAt": datetime.fromtimestamp(marks[i]).strftime("%H:%M:%S.%f")[:-3],
            "httpStatus": st,
            "code": payload.get("code"),
            "message": payload.get("message"),
            "data": payload.get("data"),
        }

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(N)]
    print("准备发起 %d 并发：room=%d %s %s-%s\n" % (N, ROOM_ID, DATE, START[-8:-3], END[-8:-3]))
    started = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - started

    spread_ms = (max(marks) - min(marks)) * 1000
    ok = [r for r in results if r["code"] == 200]
    conflict = [r for r in results if r["code"] == 409]
    other = [r for r in results if r["code"] not in (200, 409)]
    dup_key = [r for r in results
               if any(k in (json.dumps(r, ensure_ascii=False)) for k in ("uk_reservation_no", "Duplicate entry", "唯一键"))]
    server_err = [r for r in results if r["httpStatus"] is not None and r["httpStatus"] >= 500]

    print("起跑时间跨度：%.1f ms（判据 < 1000ms，%s）" % (spread_ms, "满足同一秒" if spread_ms < 1000 else "★不满足"))
    print("总耗时：%.2f s" % elapsed)
    print("成功(200)：%d 条；冲突(409)：%d 条；其它：%d 条" % (len(ok), len(conflict), len(other)))
    print("唯一键冲突报错：%d 条；5xx：%d 条" % (len(dup_key), len(server_err)))
    if ok:
        print("成功者编号：%s（id=%s）" % (ok[0]["data"].get("reservationNo"), ok[0]["data"].get("id")))
    if other:
        print("其它响应样例：%s" % json.dumps(other[:3], ensure_ascii=False)[:400])
    if conflict:
        sample = conflict[0]["data"] or {}
        print("409 载荷字段：%s" % list(sample.keys()))

    passed = (len(ok) == 1 and len(conflict) == N - 1 and not other
              and not dup_key and not server_err and spread_ms < 1000)
    print("\n判定：%s（要求 1 成功 / %d 明确失败，无 500，无唯一键冲突）" %
          ("通过" if passed else "未通过", N - 1))

    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "concurrency_%d.json" % N)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({
            "executedAt": datetime.now().isoformat(timespec="seconds"),
            "concurrency": N, "roomId": ROOM_ID, "date": DATE,
            "startTime": START, "endTime": END,
            "spreadMs": round(spread_ms, 1), "elapsedSec": round(elapsed, 2),
            "success": len(ok), "conflict": len(conflict), "other": len(other),
            "duplicateKeyErrors": len(dup_key), "serverErrors": len(server_err),
            "passed": passed,
            "accounts": ["user1", "user2"],
            "responses": results,
        }, f, ensure_ascii=False, indent=2)
    print("50 条响应明细已写入 %s" % out)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
