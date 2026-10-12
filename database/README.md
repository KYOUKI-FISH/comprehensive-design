# database/ —— 数据库脚本（第 6 周工程骨架）

> 智能会议室管理系统 V1.0 ｜ 第 4 组 ｜ MySQL 8.0.x
> 本目录对应第 5 周 DFD 的**数据存储 D1～D3**，是第 6 周工程骨架的一部分。

## 文件说明

| 文件 | 作用 |
|------|------|
| `01_schema.sql` | 建库 + 建三张表（`user` / `meeting_room` / `reservation`），含索引、外键与口径注释 |
| `02_seed.sql` | 灌入种子数据：默认账号 2 个 + 会议室 4 间 + 已占用时段 7 条 |

## 表与 DFD 存储的对应关系

| 表名 | DFD 存储 | 对应加工 | 说明 |
|------|----------|----------|------|
| `user` | **D1 用户信息** | P1 验证登录、P5 管理用户 | 账号、密码密文、角色（USER／ADMIN）、启用状态 |
| `meeting_room` | **D2 会议室信息** | P2 查询会议室、P6 管理会议室 | 名称、位置、容量、启用状态 |
| `reservation` | **D3 预约记录** | P3 提交预约、P4 维护我的预约、P7 处理预约 | 会议室、预约人、日期、起止时间、用途、状态 |

> 三张表正好覆盖 DFD 的 D1～D3，无额外表。本周不实现的功能（预约冲突检测的约束、审批流等）不预留字段。

## 时间语义与冲突判定（**关键口径，勿改**）

- 时间区间统一按**半开区间 `[start, end)`** 理解：
  - 冲突判定：`start_new < end_old` **且** `end_new > start_old`
  - **首尾相接不算冲突**：`end_new == start_old` 或 `start_new == end_old`
- 种子数据里 `主楼 A301 / 2026-10-14` 刻意放了 `09:00-10:00` 与 `10:00-11:00` 两条，
  它们是**两条独立记录、互不冲突**，用来验证上面这条规则。
- `reservation.reserve_date` 只有一个字段，因为 **FR-08 禁止跨天**，无需存结束日期。

> ⚠ 本脚本**不建"防重复预订"的数据库约束**：`UNIQUE(room_id, reserve_date, start_time, end_time)`
> 只能挡住完全相同的记录，挡不住 `09:00-10:00` 与 `09:30-10:30` 这类**部分重叠**。
> FR-09 冲突检测由应用层在事务内查询判定（第 9～10 周开发），本周只建表。

## 使用方法

### 1. 安装 MySQL 8.0

见根 `README.md`「快速启动」章节（写死版本号与下载地址）。

### 2. 初始化（一条命令建库建表）

```bash
mysql -u root -p --default-character-set=utf8mb4 < database/01_schema.sql
```

> Windows 下脚本文件是 UTF-8 编码，**必须带 `--default-character-set=utf8mb4`**，否则中文会变乱码。

### 3. 灌入种子数据（一条命令）

```bash
mysql -u root -p --default-character-set=utf8mb4 meeting_room < database/02_seed.sql
```

脚本末尾自带自检查询，应输出 `user`=2、`meeting_room`=4、`reservation`=7。

### 4. 校验

```bash
mysql -u root -p --default-character-set=utf8mb4 meeting_room < tests/sql/01_verify_seed.sql
```

## 默认账号（写死在种子数据里）

| 账号 | 密码 | 角色 | 姓名 |
|------|------|------|------|
| `student01` | `123456` | 普通用户（USER） | 张同学 |
| `admin01` | `123456` | 管理员（ADMIN） | 王管理员 |

> 密码以 **BCrypt**（cost=10）存储，密文写在 `02_seed.sql` 里，明文即上表。BCrypt 每次加盐结果不同，
> 脚本中的两个密文是固定值，均为 `123456` 的哈希，可直接用于登录。正式环境请改密。

## 常见问题

| 现象 | 原因与处理 |
|------|------------|
| 中文显示为乱码 | 执行时漏了 `--default-character-set=utf8mb4` |
| `ERROR 1049 (42000): Unknown database 'meeting_room'` | 还没执行 `01_schema.sql`，或连接时漏了库名 |
| `ERROR 1062: Duplicate entry ... for key 'PRIMARY'` | 种子数据已灌过一次；重灌请先重新执行 `01_schema.sql`（会重建库，**会丢数据**） |
| `ERROR 1452: Cannot add or update a child row` | 灌种子数据时 `reservation` 先于 `user` / `meeting_room` 执行，或先手动删过基础数据；按 `01 → 02` 顺序执行 |
| `ERROR 1045 (28000): Access denied` | root 密码不对；或未安装 MySQL 8.0（注意不是 MySQL 5.7） |
