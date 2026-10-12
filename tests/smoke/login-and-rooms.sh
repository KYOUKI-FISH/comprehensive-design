#!/usr/bin/env bash
# =============================================================================
# 冒烟测试：登录 + 查询会议室（第 6 周工程骨架）
# 小组：第 4 组
# -----------------------------------------------------------------------------
# 作用：一条命令验证「后端起来了 + 数据库连上了 + 表能查 + 登录校验有效」，
#       即第 6 周验收要看的"登录/查询最小通路"。
#
# 前置：后端已启动（默认 http://localhost:8080），数据库已按 database/ 下脚本初始化。
#
# 用法（Git Bash / WSL / Linux / macOS）：
#   bash tests/smoke/login-and-rooms.sh
#   BASE_URL=http://localhost:8080/api bash tests/smoke/login-and-rooms.sh   # 改后端地址
#
# ⚠ 本脚本需要 curl。Windows 自带的 curl.exe 已够用；若在纯 cmd 下，
#   请改用 tests/README.md 里逐条列出的 curl 命令。
# =============================================================================
set -u

BASE="${BASE_URL:-http://localhost:8080/api}"
PASS=0
FAIL=0

print_step() {
  printf '\n=== [%s] %s\n' "$1" "$2"
}

# assert_contains <用例名> <实际响应> <期望包含的片段>
assert_contains() {
  if printf '%s' "$2" | grep -q -- "$3"; then
    printf '  ✅ PASS  %s\n' "$1"
    PASS=$((PASS + 1))
  else
    printf '  ❌ FAIL  %s\n     期望包含：%s\n     实际响应：%s\n' "$1" "$3" "$2"
    FAIL=$((FAIL + 1))
  fi
}

# assert_not_contains <用例名> <实际响应> <不应出现的片段>
assert_not_contains() {
  if printf '%s' "$2" | grep -q -- "$3"; then
    printf '  ❌ FAIL  %s\n     不应包含：%s\n     实际响应：%s\n' "$1" "$3" "$2"
    FAIL=$((FAIL + 1))
  else
    printf '  ✅ PASS  %s\n' "$1"
    PASS=$((PASS + 1))
  fi
}

printf '后端地址：%s\n' "$BASE"

# ---------------------------------------------------------------------------
print_step "T1" "启动自检 GET /health —— 期望 code=200 且能数出种子数据"
resp=$(curl -s -X GET "$BASE/health")
printf '  响应：%s\n' "$resp"
assert_contains "T1-1 进程与数据库均正常" "$resp" '"code":200'
assert_contains "T1-2 能查到用户表（2 个启用账号）" "$resp" '"userCount":2'
assert_contains "T1-3 能查到会议室表（4 间，含停用）" "$resp" '"roomCount":4'

# ---------------------------------------------------------------------------
print_step "T2" "普通用户登录 POST /auth/login —— 期望成功且角色为 USER"
resp=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"student01","password":"123456"}')
printf '  响应：%s\n' "$resp"
assert_contains "T2-1 登录成功" "$resp" '"code":200'
assert_contains "T2-2 返回普通用户角色" "$resp" '"role":"USER"'

# ---------------------------------------------------------------------------
print_step "T3" "管理员登录 POST /auth/login —— 期望成功且角色为 ADMIN（FR-02 角色区分）"
resp=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin01","password":"123456"}')
printf '  响应：%s\n' "$resp"
assert_contains "T3-1 登录成功" "$resp" '"code":200'
assert_contains "T3-2 返回管理员角色" "$resp" '"role":"ADMIN"'

# ---------------------------------------------------------------------------
print_step "T4" "密码错误 POST /auth/login —— 期望 code=401 且不泄露账号是否存在"
resp=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"student01","password":"wrong-password"}')
printf '  响应：%s\n' "$resp"
assert_contains "T4-1 拒绝登录（401）" "$resp" '"code":401'

# 用一个不存在的账号，响应应与 T4 完全一致
resp2=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"no-such-user","password":"whatever"}')
printf '  响应（不存在的账号）：%s\n' "$resp2"
if [ "$resp" = "$resp2" ]; then
  printf '  ✅ PASS  T4-2 账号不存在与密码错误提示一致\n'
  PASS=$((PASS + 1))
else
  printf '  ❌ FAIL  T4-2 两种失败的响应不同，存在账号枚举风险\n'
  FAIL=$((FAIL + 1))
fi

# ---------------------------------------------------------------------------
print_step "T5" "查询会议室 GET /rooms —— 期望返回启用中的会议室"
resp=$(curl -s -X GET "$BASE/rooms")
printf '  响应：%s\n' "$resp"
assert_contains "T5-1 查询成功" "$resp" '"code":200'
assert_contains "T5-2 含「主楼 A301」" "$resp" '主楼 A301'
assert_contains "T5-3 含「图书馆 B201」" "$resp" '图书馆 B201'
assert_not_contains "T5-4 不含停用会议室（FR-06：主楼 A105 不应出现）" "$resp" '主楼 A105'

# ---------------------------------------------------------------------------
print_step "T6" "按最小人数筛选 GET /rooms?minCapacity=10 —— 期望只剩 12 人与 20 人两间"
resp=$(curl -s -X GET "$BASE/rooms?minCapacity=10")
printf '  响应：%s\n' "$resp"
assert_contains "T6-1 含 12 人的「主楼 A301」" "$resp" '主楼 A301'
assert_contains "T6-2 含 20 人的「图书馆 B201」" "$resp" '图书馆 B201'
assert_not_contains "T6-3 不含 8 人的「主楼 A302」" "$resp" '主楼 A302'

# ---------------------------------------------------------------------------
print_step "T7" "按关键字筛选 GET /rooms?keyword=图书馆 —— 期望只剩图书馆的会议室"
resp=$(curl -s -X GET "$BASE/rooms?keyword=%E5%9B%BE%E4%B9%A6%E9%A6%86")
printf '  响应：%s\n' "$resp"
assert_contains "T7-1 含「图书馆 B201」" "$resp" '图书馆 B201'
assert_not_contains "T7-2 不含主楼的会议室" "$resp" '主楼'

# ---------------------------------------------------------------------------
printf '\n========================================\n'
printf '结果：通过 %d 项，失败 %d 项\n' "$PASS" "$FAIL"
printf '========================================\n'
if [ "$FAIL" -gt 0 ]; then
  printf '存在失败用例，请对照上文"实际响应"排查；常见原因见根 README「常见问题」。\n'
  exit 1
fi
printf '全部通过：登录/查询最小通路可用。\n'
exit 0
