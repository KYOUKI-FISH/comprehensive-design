# tests/week11 —— 第 11 周（测试、部署与修复）测试脚本

> 作者：虞业宸（2025091202009，Git: `yycyuyechen`）
> 建立于 2026-11-04，对应 `docs/week11/周推进计划_第11周.md` §2.1.2

## 这个目录放什么

| 文件 | 覆盖 | 对应记录 |
|------|------|----------|
| `01_func_exception_cases.py` | 功能／异常类用例 TC-01～TC-06（6 条） | `docs/week11/04_虞业宸_功能异常用例执行记录.md` |
| `02_concurrency_50.py` | 并发超卖：50 个请求同一秒抢同一时段（`threading.Barrier` 同时起跑） | `docs/week11/05_…取证记录.md` §1 |
| `03_sql_injection.py` | SQL 注入 16 次（4 组载荷 × 4 个注入点），带安全边界自检 | 同上 §2 |
| `04_perf_explain.sql` | 隔离库灌 1000 条 ＋ 5 段 EXPLAIN（含改写前后对比与等价性核对） | 同上 §3 |
| `05_perf_bench.sql` | 两种 `correctOverduePending` 写法的扫描耗时对比 | 同上 §3.3 |
| `out/` | 每次执行的原始结果（JSON／终端输出），**报告引用的就是这些文件** | — |

## 前置条件

1. MySQL 8.0.46 在 3306 监听（本机数据目录是 `data2`，root 空口令）；
2. 后端在 8080 监听，且**数据库口令用空串**：
   `export DB_PASSWORD='' && java -jar backend/target/*.jar`；
3. Python 3（脚本只用标准库，无需装依赖）。

## 三个必须知道的坑

| 坑 | 现象 | 处理 |
|----|------|------|
| 沙箱设了 `HTTP(S)_PROXY=127.0.0.1` | 请求本机后端报 502／10061，像"后端挂了" | 脚本已内置绕过；手工 `curl` 要加 `--noproxy '*'` |
| 测试日期撞上第 9／10 周历史占用 | "预期成功"的用例返回 409 | 用例脚本自带清理、并发脚本用「今天 + N 天」的干净日期（`W11_DAY_OFFSET` 可调） |
| 并发脚本重跑同一天 | 上一轮的胜出者会把这一轮全判成冲突 | 两次执行换 `W11_DAY_OFFSET`，或先清理该日数据 |

## 复跑

```bash
python tests/week11/01_func_exception_cases.py
W11_DAY_OFFSET=50 python tests/week11/02_concurrency_50.py 50
python tests/week11/03_sql_injection.py
mysql -uroot -P3306 -h127.0.0.1 --table < tests/week11/04_perf_explain.sql
mysql -uroot -P3306 -h127.0.0.1 --table < tests/week11/05_perf_bench.sql
```

> ⚠️ 破坏性测试只允许打 `localhost:8080`；脚本对非本机地址会直接退出。
