# 前端 API 契约假设（第 9 周）

用途：本文件列出前端 4 个页面实际调用后端时**依赖的字段、状态码和错误结构**。第 7—8 周已冻结接口清单；本文件不新增接口，只把“前端到底期望什么”写清楚，便于与组后端联调时逐项对齐。

基线文件：`docs/week07-08/02_数据库与接口设计.md`（第 8 周定稿，14 条接口）。
本周变更：D1（不新增 `GET /api/rooms/{id}`）、D2（占用时段接口升为必须）、D3（服务端随机串令牌 + 30 分钟有效期），见 `docs/week09/01_接口变更记录.md`（由李知渔维护）。

## 1. 统一返回体

成功（HTTP 200）：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {}
}
```

失败（HTTP 4xx/5xx）：

```json
{
  "code": 40901,
  "message": "该时段已被占用，请选择其他时间",
  "data": {
    "occupiedSlots": [
      { "startTime": "2026-10-27T10:00:00", "endTime": "2026-10-27T11:00:00" }
    ]
  }
}
```

前端约定：

- 以 HTTP 状态码判断成功/失败；`message` 直接展示给用户（必须是面向使用者的中文）。
- 业务失败不要返回 500，也不要把堆栈或原始错误码当 `message`（NF-10）。
- 401 且请求携带令牌：前端清除本地会话并跳转登录页。
- 409：前端从 `data.occupiedSlots` 取冲突时段；没有该字段时只显示 `message`，页面不会崩。

## 2. 认证

### 2.1 `POST /api/auth/login`

无需令牌。

请求：

```json
{ "username": "user001", "password": "******" }
```

响应 `data`：

```json
{
  "token": "64 位随机串",
  "expiresIn": 1800,
  "user": {
    "id": 1,
    "username": "user001",
    "name": "张同学",
    "department": "计算机学院",
    "role": "USER",
    "status": "ACTIVE"
  }
}
```

前端假设：

- `token` 必填，放在后续请求的 `Authorization: Bearer <token>`。
- `expiresIn` 单位为秒；缺省时前端按 30 分钟处理。
- `user.role` 为 `USER` / `ADMIN`（大小写不敏感，前端会转大写）；`user.status` 为 `ACTIVE` / `DISABLED`。
- 账号不存在、口令错误：HTTP 401，`message` 相同（例如“账号或密码错误”），避免账号枚举。
- 账号停用：HTTP 403，`message` 含“账号已停用”。

### 2.2 `POST /api/auth/logout`

需要令牌。成功返回 HTTP 200，服务端使当前令牌立即失效。

## 3. 会议室

### 3.1 `GET /api/rooms`

需要令牌。查询参数均可选：

| 参数 | 类型 | 说明 |
|------|------|------|
| `name` | string | 名称模糊匹配 |
| `location` | string | 位置模糊匹配 |
| `minCapacity` | number | 最小容量，正整数 |

响应 `data`：`RoomVO[]`

```json
[
  {
    "id": 1,
    "name": "学术报告厅",
    "location": "图书馆B1",
    "capacity": 120,
    "equipment": ["投影仪", "音响", "麦克风"],
    "status": "AVAILABLE"
  }
]
```

前端假设：

- `status` 为 `AVAILABLE` / `DISABLED`；`DISABLED` 在列表显示“已停用”，且**不出现在预约表单的会议室下拉框**（FR-06）。
- `equipment` 允许数组或逗号分隔字符串；前端 `formatEquipment()` 两种都能显示。
- 无匹配结果返回空数组 `[]`，不要返回 404；页面显示“未找到符合条件的会议室”。
- 本接口返回完整 `RoomVO`，详情页基本信息按 `id` 从此列表取（D1），不调用 `GET /api/rooms/{id}`。

### 3.2 `GET /api/rooms/{id}/occupancy?date=YYYY-MM-DD`

需要令牌。`date` 为本地日期，必填。

响应 `data`：

```json
{
  "roomId": 1,
  "roomName": "学术报告厅",
  "date": "2026-10-27",
  "occupiedSlots": [
    { "startTime": "2026-10-27T10:00:00", "endTime": "2026-10-27T11:00:00" }
  ],
  "freeSlots": [
    { "startTime": "2026-10-27T08:00:00", "endTime": "2026-10-27T10:00:00" },
    { "startTime": "2026-10-27T11:00:00", "endTime": "2026-10-27T22:00:00" }
  ]
}
```

前端假设：

- 时间字符串为**本地时间**，格式 `YYYY-MM-DDTHH:mm:ss`（也兼容空格分隔）。
- `occupiedSlots` 只包含未取消的预约；`freeSlots` 可选，若后端不返回，详情页只显示已占用时段，不伪造空闲时段。
- 会议室不存在返回 404；日期非法返回 400，`message` 为中文。
- 该接口的占用查询与 `POST /api/bookings` 的冲突检测必须使用同一套数据（周计划 §3.1 硬要求），不要两处各写一套。

## 4. 预约

### 4.1 `POST /api/bookings`

需要令牌，且角色必须是 `USER`（管理员返回 403）。

请求：

```json
{
  "roomId": 1,
  "subject": "课题组周会",
  "attendees": 15,
  "contact": "张同学",
  "startTime": "2026-10-27T11:00:00",
  "endTime": "2026-10-27T12:00:00"
}
```

前端假设的校验顺序与错误：

| 场景 | HTTP | message 示例 |
|------|------|-------------|
| 缺少会议室/主题/人数/联系人/起止时间 | 400 | 请补充以下必填项：会议主题、参会人数 |
| 结束时间不晚于开始时间 | 400 | 结束时间必须晚于开始时间 |
| 跨天 | 400 | 单次预约不能跨天 |
| 超过 8 小时 | 400 | 单次预约不能超过 8 小时 |
| 会议室不存在 | 404 | 未找到该会议室 |
| 会议室已停用 | 400 | 该会议室已停用，无法提交预约 |
| 同一会议室时间冲突 | 409 | 该时段已被占用，请选择其他时间 |
| 管理员提交 | 403 | 管理员账号不允许提交预约 |

冲突判定的唯一定义（FR-09）：

```text
start_new < end_old AND end_new > start_old
```

半开区间 `[start, end)`，首尾相接不算冲突。**不得写成 `<=`。**

409 的 `data.occupiedSlots` 是 FR-10 的数据来源；前端会把每个时段格式化为“2026-10-27 10:00 — 11:00”展示，并保留表单已填内容（FR-18）。

成功响应 `data`：

```json
{
  "id": 4,
  "reservationNo": "MR202610270004",
  "roomId": 1,
  "roomName": "学术报告厅",
  "subject": "E2E 冲突验证会议",
  "attendees": 10,
  "contact": "张同学",
  "startTime": "2026-10-27T11:00:00",
  "endTime": "2026-10-27T12:00:00",
  "status": "PENDING",
  "createdAt": "2026-10-26T18:00:00"
}
```

前端假设：

- `reservationNo` 必须返回并展示；格式建议 `MR + yyyyMMdd + 流水号`，但页面只做非空展示，不校验长度。
- `status` 四态：`PENDING` / `ONGOING` / `FINISHED` / `CANCELLED`；新建为 `PENDING`（页面显示“待开始”）。
- 成功页展示 `roomName`、起止时间、主题、人数、联系人、状态；缺字段时页面显示空，不会崩。

### 4.2 `GET /api/bookings/mine`

第 9 周**不实现**。前端“我的预约”入口是占位页，不调用该接口。第 10 周实现后，前端需要补列表/修改/取消页面。

## 5. 管理接口（第 9 周不调用）

前端管理菜单指向 `/admin/users`、`/admin/rooms`、`/admin/bookings`、`/admin/logs`，本周是“建设中”占位页，不调用管理接口。

安全要求（NF-03）：普通用户令牌直接调管理接口必须返回 403。前端路由守卫只负责页面跳转，**不是安全边界**。

## 6. 与组后端联调时的适配点

如果第 8 周定稿的字段与本文不同，按以下顺序适配，不要在页面组件里散落兼容判断：

| 可能差异 | 适配位置 |
|----------|----------|
| 列表返回 `{ list: [...] }` 或分页结构 | `frontend/src/api/room.ts` 取出数组 |
| 角色返回 `0/1` 或 `'admin'` | `frontend/src/session/session.ts` 的 `saveSession()` 做映射 |
| 会议室状态为 `ENABLED/DISABLED`、`1/0` | `frontend/src/types/api.ts` + `utils/labels.ts` 映射 |
| 冲突响应字段叫 `conflicts` 而非 `occupiedSlots` | `frontend/src/api/booking.ts` 的 `extractOccupiedSlots()` |
| 时间格式为 `YYYY-MM-DD HH:mm:ss` | `frontend/src/utils/datetime.ts` 的 `normalizeDateTime()` 已兼容空格 |
| 登录不返回 `expiresIn` | `session.ts` 已有 30 分钟兜底 |
| 占用接口不返回 `freeSlots` | `RoomDetailView.vue` 只显示已占用时段，不补假数据 |

## 7. mock 与正式后端的差异

| 维度 | mock（本目录） | 正式后端（第 8 周设计） |
|------|----------------|--------------------------|
| 数据 | 进程内存，重启重置 | MySQL 4 张表 |
| 口令 | scrypt 摘要，随机演示口令 | BCrypt 摘要，账号由管理员创建 |
| 令牌 | 进程内 `Map`，30 分钟 | 服务端签发 + 失效标记 + 有效期 |
| 冲突并发 | 单进程串行 | 事务 + `SELECT ... FOR UPDATE` 会议室行锁 |
| 日志 | 控制台 | `operation_log` 表 |
| 用途 | 本地自测/演示 | 正式验收 |

mock 只用于让前端四个页面“真打接口、真看到 409”，不能替代正式后端。
