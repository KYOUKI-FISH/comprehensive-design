# 第 11 周四类攻击取证记录

> 目标主机：`http://localhost:8080`（**仅本机测试环境**，未对学校在用系统、其他课程系统或公网服务发起任何请求）。
> 执行时间：2026-11-03 23:06；测试账号：`user1` / `user2`（演示账号，口令见根 README）。
> 取证三样：请求（方法 + 完整 URL + 关键头体）、响应（HTTP 状态码 + 响应体）、库内数据（执行前后 `SELECT` 结果）。

## A. 越权取消（用户 B 删用户 A 的预约）


#### A-1 用 user2 的令牌取消 user1 的预约 #1052

- **请求**：`DELETE http://localhost:8080/api/bookings/1052`
  - 请求头/体：Authorization: Bearer <user2 令牌>（无请求体）
- **响应**：HTTP `200`，响应体：`{'code': 403, 'message': '只能取消本人提交的预约', 'data': None}`
- **库内数据**：执行前 `MR202612231052/PENDING` → 执行后 `MR202612231052/PENDING`
- **预期**：403 / 权限不足；user1 的预约仍在库、状态不变（仍为 PENDING）

**判定**：通过（HTTP `200`，业务码 `403`）

## B. 越权查看（普通用户查管理端数据）


#### B-1 普通用户请求全部预约

- **请求**：`GET http://localhost:8080/api/admin/bookings`
  - 请求头/体：Authorization: Bearer <user1 令牌>
- **响应**：HTTP `200`，响应体：`{'code': 403, 'message': '无权访问该功能', 'data': None}`
- **库内数据**：执行前 `1001` → 执行后 `1001`
- **预期**：403 / 权限不足；响应体不含任何他人预约数据

#### B-2 普通用户请求操作日志

- **请求**：`GET http://localhost:8080/api/admin/logs`
  - 请求头/体：Authorization: Bearer <user1 令牌>
- **响应**：HTTP `200`，响应体：`{'code': 403, 'message': '无权访问该功能', 'data': None}`
- **库内数据**：执行前 `1001` → 执行后 `1001`
- **预期**：403 / 权限不足；响应体不含日志数据

**判定**：通过（业务码 B-1=`403`、B-2=`403`，HTTP=200/200）

## C. 时间边界（跨天 / 超 8 小时 / 结束早于开始 / 首尾相接）


#### C-1 跨天（22:00 → 次日 02:00）

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2026-12-18', 'startTime': '2026-12-18 22:00', 'endTime': '2026-12-19 02:00', 'topic': '边界用例-跨天', 'attendeeCount': 2, 'contact': '边界测试'}
- **响应**：HTTP `200`，响应体：`{'code': 400, 'message': '预约不能跨天，且单次不能超过 8 小时', 'data': None}`
- **库内数据**：执行前 `1001` → 执行后 `1001`
- **预期**：400 / 提示不能跨天；库内条数不变

#### C-2 单次超 8 小时（08:00 → 20:00）

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2026-12-18', 'startTime': '2026-12-18 08:00', 'endTime': '2026-12-18 20:00', 'topic': '边界用例-超8小时', 'attendeeCount': 2, 'contact': '边界测试'}
- **响应**：HTTP `200`，响应体：`{'code': 400, 'message': '预约不能跨天，且单次不能超过 8 小时', 'data': None}`
- **库内数据**：执行前 `1001` → 执行后 `1001`
- **预期**：400 / 提示时长上限；库内条数不变

#### C-3 结束时间早于开始时间（15:00 → 14:00）

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2026-12-18', 'startTime': '2026-12-18 15:00', 'endTime': '2026-12-18 14:00', 'topic': '边界用例-逆序', 'attendeeCount': 2, 'contact': '边界测试'}
- **响应**：HTTP `200`，响应体：`{'code': 400, 'message': '结束时间必须晚于开始时间', 'data': None}`
- **库内数据**：执行前 `1001` → 执行后 `1001`
- **预期**：400 / 提示时间不合法；库内条数不变

#### C-4 首尾相接（先 21:00-22:00，再 22:00-23:00）

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2026-12-18', 'startTime': '2026-12-18 22:00', 'endTime': '2026-12-18 23:00', 'topic': '边界用例-首尾相接', 'attendeeCount': 2, 'contact': '边界测试'}
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': {'id': 1055, 'reservationNo': 'MR202612181055', 'status': 'PENDING'}}`
- **库内数据**：执行前 `1002` → 执行后 `1003`
- **预期**：200 / 提交成功（半开区间 [start,end)，首尾相接不算冲突）；库内两条并存，计数=2

**判定**：前三条拒绝 + 第四条放行 = 通过；`<=`/`<` 写反时第四条会被误拒，此用例专门挡这个。

## D. SQL 注入（用户可控入参塞载荷）


#### D-1.1 登录账号名注入：' OR '1'='1

- **请求**：`POST http://localhost:8080/api/auth/login`
  - 请求头/体：body: {'username': "' OR '1'='1", 'password': "' OR '1'='1"}
- **响应**：HTTP `200`，响应体：`{'code': 401, 'message': '账号或密码错误', 'data': None}`
- **预期**：401 / 用户名或口令错误（当作普通字符串比对），不返回令牌、不抛 500

#### D-2.1 会议室位置筛选注入：' OR '1'='1

- **请求**：`GET http://localhost:8080/api/rooms?location=' OR '1'='1`
  - 请求头/体：Authorization: Bearer <user1 令牌>
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': []}`
- **预期**：200 / 查不到（返回 0 条，不当条件执行）；不报 500

#### D-3.1 预约主题/联系人注入：' OR '1'='1

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2027-01-03', 'startTime': '2027-01-03 19:00', 'endTime': '2027-01-03 20:00', 'topic': "' OR '1'='1", 'attendeeCount': 2, 'contact': "' OR '1'='1"}
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': {'id': 1056, 'reservationNo': 'MR202701031056', 'status': 'PENDING'}}`
- **预期**：200 落库（MyBatis `#{}` 参数绑定，载荷被当普通字符串原样存储）；库内 topic 原样为 ' OR '1'='1

#### D-1.2 登录账号名注入：1; DROP TABLE reservation--

- **请求**：`POST http://localhost:8080/api/auth/login`
  - 请求头/体：body: {'username': '1; DROP TABLE reservation--', 'password': '1; DROP TABLE reservation--'}
- **响应**：HTTP `200`，响应体：`{'code': 401, 'message': '账号或密码错误', 'data': None}`
- **预期**：401 / 用户名或口令错误（当作普通字符串比对），不返回令牌、不抛 500

#### D-2.2 会议室位置筛选注入：1; DROP TABLE reservation--

- **请求**：`GET http://localhost:8080/api/rooms?location=1; DROP TABLE reservation--`
  - 请求头/体：Authorization: Bearer <user1 令牌>
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': []}`
- **预期**：200 / 查不到（返回 0 条，不当条件执行）；不报 500

#### D-3.2 预约主题/联系人注入：1; DROP TABLE reservation--

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2027-01-04', 'startTime': '2027-01-04 19:00', 'endTime': '2027-01-04 20:00', 'topic': '1; DROP TABLE reservation--', 'attendeeCount': 2, 'contact': '1; DROP TABLE reservation--'…
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': {'id': 1057, 'reservationNo': 'MR202701041057', 'status': 'PENDING'}}`
- **预期**：200 落库（MyBatis `#{}` 参数绑定，载荷被当普通字符串原样存储）；库内 topic 原样为 1; DROP TABLE reservation--

#### D-1.3 登录账号名注入：'; UPDATE user SET role='ADMIN' WHERE 1=1-- 

- **请求**：`POST http://localhost:8080/api/auth/login`
  - 请求头/体：body: {'username': "'; UPDATE user SET role='ADMIN' WHERE 1=1-- ", 'password': "'; UPDATE user SET role='ADMIN' WHERE 1=1-- "}
- **响应**：HTTP `200`，响应体：`{'code': 401, 'message': '账号或密码错误', 'data': None}`
- **预期**：401 / 用户名或口令错误（当作普通字符串比对），不返回令牌、不抛 500

#### D-2.3 会议室位置筛选注入：'; UPDATE user SET role='ADMIN' WHERE 1=1-- 

- **请求**：`GET http://localhost:8080/api/rooms?location='; UPDATE user SET role='ADMIN' WHERE 1=1-- `
  - 请求头/体：Authorization: Bearer <user1 令牌>
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': []}`
- **预期**：200 / 查不到（返回 0 条，不当条件执行）；不报 500

#### D-3.3 预约主题/联系人注入：'; UPDATE user SET role='ADMIN' WHERE 1=1-- 

- **请求**：`POST http://localhost:8080/api/bookings`
  - 请求头/体：body: {'roomId': 1, 'date': '2027-01-05', 'startTime': '2027-01-05 19:00', 'endTime': '2027-01-05 20:00', 'topic': "'; UPDATE user SET role='ADMIN' WHERE 1=1-- ", 'attendeeCount': 2, 'contact': "'; UPDATE u…
- **响应**：HTTP `200`，响应体：`{'code': 200, 'message': 'ok', 'data': {'id': 1058, 'reservationNo': 'MR202701051058', 'status': 'PENDING'}}`
- **预期**：200 落库（MyBatis `#{}` 参数绑定，载荷被当普通字符串原样存储）；库内 topic 原样为 '; UPDATE user SET role='ADMIN' WHERE 1=1--

### D 汇总：库结构与数据完好性

| 项 | 注入前 | 注入后 | 结论 |
|---|---|---|---|
| `meeting_room` 库内表数量 | 4 | 4 | 完好 |
| `reservation` 条数 | 1003 | 1006 | 只增不减（D-3 合法落库导致 +n），无 DROP/TRUNCATE 痕迹 |

**判定**：三类载荷（登录名 / 位置筛选 / 预约主题）均被当作普通字符串处理，无 500、无执行痕迹，表结构与数据完好 —— 「打不动」本身即结论。
