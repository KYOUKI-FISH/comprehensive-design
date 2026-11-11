# BUG-07 性能回归复跑记录（1000 条 P95 与 EXPLAIN）

| 项目 | 内容 |
|------|------|
| 执行人 | 李知渔（组长） |
| 执行日期 | 2026-11-11 20:12—20:15 |
| 回归项 | `01_接口响应口径变更说明.md` §5.7 第 2 项「1000 条数据下的 P95 抽样与 EXPLAIN 复核」 |
| 被测版本 | `main` 分支 `823db1b`（BUG-07 双写修复、九组冲突回归之后） |
| 前置条件 | Java 17 ／ Spring Boot 3.2.5 ／ MySQL 8.0；按根 README 三步启动，启动前设置 `DB_PASSWORD`；以 `/api/health` 的 `database` 为 `UP` 作为连库判据 |
| 使用脚本 | `tests/week11/seed_1000.py`、`tests/week11/perf_baseline.py`（仓库内既有脚本，本次未修改） |
| 结论 | **该项复跑通过**。BUG-07 仍为「已修复（待回归确认）」——§5.7 三项中仅剩页面级完整 12 步未复跑 |

## 1. 为什么补这一项

第 11 周已在 1000 条规模下取得性能基线（P95 31.6／36.6 ms、EXPLAIN 无 `type=ALL`）。第 12 周 BUG-07 把响应改为 HTTP 状态行与 `body.code` 双写，涉及异常处理器、鉴权拦截器与前端请求封装，属跨层行为变更，因此 `01_接口响应口径变更说明.md` §5.7 把「1000 条 P95／EXPLAIN」列为三项回归之一。九组冲突已由虞业宸 11-11 复跑 9/9 通过（`04_虞业宸_BUG-07回归复跑记录（九组冲突+50并发）.md`），本记录补齐第 2 项。

## 2. 执行步骤

1. 启动后端，`GET /api/health` 返回 `database: UP`（见 `evidence/bug07_perf_1000_db_restore.txt` 末尾）。灌数前 `reservation` 为 **15 条**、`max_id=67`、`AUTO_INCREMENT=82`。
2. 灌数：`python tests/week11/seed_1000.py 1000`，`reservation` 由 **15 → 1000 条**，编号去重数 **1000**（无重复编号）。
3. 性能：`python tests/week11/perf_baseline.py 20`，两个接口各 20 次，全部返回 200；脚本同时执行三条 EXPLAIN。
4. 数据恢复：删除本次造数行后 `reservation` 回到 **15 条**、`max_id=67`、`AUTO_INCREMENT=82`、编号去重 15，与灌数前完全一致；`/api/health` 仍为 `UP`。

> 造数只写 `reservation` 一张表，且以 `contact='perf'` 精确标记，恢复按该条件删除并复原自增计数器，不触碰业务种子数据。

## 3. 结果

### 3.1 两个接口各 20 次（阈值 P95 < 500 ms）

| 接口 | 成功 | 返回行数 | min / avg / max (ms) | **P95 (ms)** | 判定 |
|------|------|----------|----------------------|--------------|------|
| `GET /api/rooms?date=…` | 20/20 | 3 | 9.0 / 26.7 / 70.8 | **32.5** | 达标 |
| `GET /api/bookings/mine` | 20/20 | 337 | 14.4 / 27.7 / 55.9 | **34.4** | 达标 |

与第 11 周基线对比：31.6 → **32.5** ms（+0.9）、36.6 → **34.4** ms（−2.2），均在毫秒级波动范围内，**无性能退化**。第 11 周报告中的 31.6／36.6 ms 是当时的真实取证，按「失败用例不删不改、历史记录不回改」口径保留。

### 3.2 EXPLAIN（三条查询）

| 查询 | type | key | rows | Extra |
|------|------|-----|------|-------|
| 会议室列表 `selectByCondition` | `index` | `PRIMARY` | 3 | — |
| 当日占用时段 `selectOccupiedSlots` | `range` | `idx_reservation_room_time` | 2 | Using index condition; Using where |
| 我的预约 `selectMine`（`r`） | `ref` | `idx_reservation_user_date` | 337 | Using filesort |
| 我的预约 `selectMine`（`m`） | `eq_ref` | `PRIMARY` | 1 | — |

**无 `type=ALL`（全表扫描），`key` 均非 NULL**，与第 8 周索引设计一致；`Using filesort` 来自 `ORDER BY start_time DESC, id DESC`，在 337 行规模下 P95 仍为 34.4 ms，按第 11 周结论不加索引、不改表结构。

## 4. 效力边界（不得据此关别的东西）

1. 只覆盖**查询侧**性能与索引，**不覆盖页面级完整 12 步**（含重叠冲突提示、修改预约、管理员四步）；
2. 不覆盖 TC-14／TC-15 干净机复现（须未参与搭建者执行）；
3. 不改写第 11 周报告的历史数字与任何失败用例结论；
4. 因此 BUG-07 状态统一写「**已修复（待回归确认）**」，**不宣布关闭**；`01_测试报告.md` 关闭确认表中组长的签字仍待页面 12 步复跑后补。

## 5. 证据索引

| 文件 | 内容 |
|------|------|
| `evidence/bug07_perf_1000_regression.json` | `perf_baseline.py` 原始输出：逐次耗时、P95、返回行数、EXPLAIN 行 |
| `evidence/bug07_perf_1000_regression.txt` | 同一次执行的完整标准输出（含「判定：★ 通过」） |
| `evidence/bug07_perf_1000_db_restore.txt` | 恢复后库内核对（15 条／`perf_rows=0`／`max_id=67`／`AUTO_INCREMENT=82`）与 `/api/health` |
| `04_虞业宸_BUG-07回归复跑记录（九组冲突+50并发）.md` | §5.7 第 1 项，11-11 已复跑通过 |

复跑方式：按根 README 三步启动 → `python tests/week11/seed_1000.py 1000` → `python tests/week11/perf_baseline.py 20` → 删除 `contact='perf'` 行并复原自增计数器。
