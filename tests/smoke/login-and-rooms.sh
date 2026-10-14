#!/usr/bin/env bash
# =============================================================================
# 冒烟测试：登录 + 查询会议室（第 6 周工程骨架）
# 小组：第 4 组
# -----------------------------------------------------------------------------
# 作用：一条命令验证「后端起来了 + 数据库连上了 + 表能查 + 登录校验有效 + 密码摘要比对有效」，
#       即第 6 周验收要看的"登录/查询最小通路"。
#
# 前置：后端已启动（默认 http://localhost:8080），数据库已按 database/ 下脚本初始化。
#
# 用法（Git Bash / WSL / Linux / macOS）：
#   bash tests/smoke/login-and-rooms.sh
#   BASE_URL=http://localhost:8080/api bash tests/smoke/login-and-rooms.sh   # 改后端地址
#
# ⚠ 本脚本需要 curl。Windows 自带的 curl.exe 已够用；若在 cmd 下，
#   请改用根 README「判断是否跑通」一节里逐条列出的命令。
# =============================================================================
set -u

BASE="${BASE_URL:-http://localhost:8080/api}"
TODAY="$(date +%F)"
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
printf '查询日期：%s（种子数据按 CURDATE() 动态生成，故须当天跑）\n' "$TODAY"

# ---------------------------------------------------------------------------
print_step "T1" "启动自检 GET /health —— 期望 code=200 且 database=UP"
resp=$(curl -s -X GET "$BASE/health")
printf '  响应：%s\n' "$resp"
assert_contains "T1-1 进程正常返回统一返回体" "$resp" '"code":200'
assert_contains "T1-2 数据库已连通" "$resp" '"database":"UP"'

# ---------------------------------------------------------------------------
print_step "T2" "普通用户登录 POST /auth/login —— 期望成功且角色为 USER（同时验证 BCrypt 摘要比对生效）"
resp=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"123456"}')
printf '  响应：%s\n' "$resp"
assert_contains "T2-1 登录成功" "$resp" '"code":200'
assert_contains "T2-2 返回普通用户角色" "$resp" '"role":"USER"'

# ---------------------------------------------------------------------------
print_step "T3" "管理员登录 POST /auth/login —— 期望成功且角色为 ADMIN（FR-02 角色区分）"
resp=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}')
printf '  响应：%s\n' "$resp"
assert_contains "T3-1 登录成功" "$resp" '"code":200'
assert_contains "T3-2 返回管理员角色" "$resp" '"role":"ADMIN"'

# ---------------------------------------------------------------------------
print_step "T4" "密码错误 POST /auth/login —— 期望 code=401 且不泄露账号是否存在"
resp=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"wrong-password"}')
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
print_step "T5" "查询启用中的会议室 GET /rooms?status=ENABLED —— 期望 3 间"
resp=$(curl -s -X GET "$BASE/rooms?status=ENABLED")
printf '  响应：%s\n' "$resp"
assert_contains "T5-1 查询成功" "$resp" '"code":200'
assert_contains "T5-2 含「逸夫楼A101会议室」" "$resp" '逸夫楼A101会议室'
assert_contains "T5-3 含「图书馆B301培训室」" "$resp" '图书馆B301培训室'
assert_not_contains "T5-4 不含停用会议室（FR-06：学生活动中心 C102 不应出现）" "$resp" '学生活动中心C102洽谈室'

# ---------------------------------------------------------------------------
print_step "T6" "按最小人数筛选 GET /rooms?status=ENABLED&minCapacity=10 —— 期望只剩 20 人与 50 人两间"
resp=$(curl -s -X GET "$BASE/rooms?status=ENABLED&minCapacity=10")
printf '  响应：%s\n' "$resp"
assert_contains "T6-1 含 20 人的「逸夫楼A101会议室」" "$resp" '逸夫楼A101会议室'
assert_contains "T6-2 含 50 人的「图书馆B301培训室」" "$resp" '图书馆B301培训室'
assert_not_contains "T6-3 不含 8 人的「逸夫楼A205研讨室」" "$resp" '逸夫楼A205研讨室'

# ---------------------------------------------------------------------------
print_step "T7" "按位置关键字筛选 GET /rooms?location=逸夫楼 —— 期望只剩逸夫楼的会议室"
resp=$(curl -s -X GET "$BASE/rooms?status=ENABLED&location=%E9%80%B8%E5%A4%AB%E6%A5%BC")
printf '  响应：%s\n' "$resp"
assert_contains "T7-1 含「逸夫楼A101会议室」" "$resp" '逸夫楼A101会议室'
assert_contains "T7-2 含「逸夫楼A205研讨室」" "$resp" '逸夫楼A205研讨室'
assert_not_contains "T7-3 不含图书馆的会议室" "$resp" '图书馆'

# ---------------------------------------------------------------------------
print_step "T8" "带日期查询 GET /rooms?status=ENABLED&date=<今天> —— 期望附带当日已占用时段"
resp=$(curl -s -X GET "$BASE/rooms?status=ENABLED&date=$TODAY")
printf '  响应：%s\n' "$resp"
assert_contains "T8-1 含 A101 的 09:00-10:00" "$resp" '09:00-10:00'
assert_contains "T8-2 含 A101 的 10:00-11:00（与上一条首尾相接，两者不冲突）" "$resp" '10:00-11:00'
assert_contains "T8-3 含 A101 的 14:00-16:00" "$resp" '14:00-16:00'

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
