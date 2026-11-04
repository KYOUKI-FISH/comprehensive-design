# -*- coding: utf-8 -*-
"""第 11 周测试脚本的公共工具（HTTP 调用 / 库内核对 / 证据留存）。

设计口径：
1. **不把口令写进脚本**——数据库口令从环境变量 `DB_PASSWORD` 读；读不到就跳过库内核对并
   明确打印"已跳过"，绝不假装查过（NF-05、交付前核对项 21）。
2. **必须绕开本机代理**——沙箱/本机设了 `HTTP_PROXY` 指向 127.0.0.1，urllib 会走代理，
   代理转发不到本机后端 → 报 502。这里统一 `ProxyHandler({})`。
3. **攻击与压测的目标地址只能是 `localhost:8080`**（任务书第三节边界），
   `assert_local()` 在每次发请求前校验，防止误伤学校在用系统或公网服务。
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

for _k in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    os.environ.pop(_k, None)
urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))

BASE = os.environ.get("WK11_BASE", "http://localhost:8080/api")
HOST_ALLOW = ("localhost", "127.0.0.1")

MYSQL_EXE = os.environ.get("WK11_MYSQL", "mysql")
DB_NAME = os.environ.get("WK11_DB", "meeting_room")


def assert_local(url):
    """边界校验：任何测试请求只允许打本机后端。"""
    for host in HOST_ALLOW:
        if ("//" + host) in url:
            return
    raise SystemExit("拒绝执行：目标地址 %s 不是本机后端（任务书第三节边界）" % url)


def login(username, password="123456"):
    """登录拿令牌；返回 (status_code, body)。"""
    return call("POST", "/auth/login", {"username": username, "password": password})


def call(method, path, body=None, token=None, raw=None):
    """发一次请求，返回 (http_status, parsed_body, elapsed_ms)。

    ★ 注意：这里返回的是 **HTTP 状态码**，不是响应体里的业务 code。
      本项目业务失败走 HTTP 4xx（400/401/403/409），成功 200，二者一致；
      但取证时两个都要留，故 body 原样返回。
    """
    url = BASE + path
    assert_local(url)
    data = json.dumps(body).encode("utf-8") if body is not None else (raw or None)
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            text = resp.read().decode("utf-8", "replace")
            elapsed = (time.perf_counter() - started) * 1000
            return resp.status, _parse(text), elapsed
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", "replace")
        elapsed = (time.perf_counter() - started) * 1000
        return e.code, _parse(text), elapsed
    except Exception as e:  # 连接失败、超时
        elapsed = (time.perf_counter() - started) * 1000
        return None, {"error": str(e)}, elapsed


def _parse(text):
    try:
        return json.loads(text)
    except Exception:
        return {"raw": text}


def db(sql):
    """执行一条 SQL，返回按行拆好的列表；口令缺失时返回 None（= 未核对）。"""
    pwd = os.environ.get("DB_PASSWORD")
    if not pwd:
        return None
    cmd = [
        MYSQL_EXE, "--protocol=TCP", "-h", "127.0.0.1", "-P", "3306",
        "-u", os.environ.get("DB_USERNAME", "root"), "-p" + pwd,
        "--ssl-mode=DISABLED", "--get-server-public-key",
        "--default-character-set=utf8mb4", "-N", "-B", "-e",
        "USE %s; %s" % (DB_NAME, sql),
    ]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        return {"error": (r.stderr or "").strip()}
    out = (r.stdout or "").strip()
    if not out:
        return []
    return [line.split("\t") for line in out.splitlines()]


def db_scalar(sql, default="?"):
    rows = db(sql)
    if rows is None:
        return None
    if isinstance(rows, dict):
        return "ERR"
    if not rows or not rows[0]:
        return default
    return rows[0][0]


def say(msg=""):
    print(msg, flush=True)


def p95(values):
    """最近秩法 P95（20 个样本取第 19 个，保守口径）。"""
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = max(0, min(len(ordered) - 1, int(round(0.95 * (len(ordered) - 1) + 0.5)) - 1))
    return ordered[idx]


def save_json(path, payload):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    say("证据已写入：%s" % path)


def require_token(username, password="123456"):
    status, body, _ = login(username, password)
    if status != 200 or (body or {}).get("code") != 200:
        say("登录失败：%s %s" % (status, body))
        sys.exit(1)
    return body["data"]["token"]

