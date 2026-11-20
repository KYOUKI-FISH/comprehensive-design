# XSS 注入补充取证记录（2026-11-20）

> **性质说明**：任务书「四类攻击」是**越权取消／越权查看／时间边界／SQL 注入**（见 `../周推进计划_第11周.md` §2.1.2.3、`../02_非功能与破坏性测试记录.md` §3），
> XSS 不在四类之内。本文件是针对演示材料中 XSS 表述的**补充实测**，不替代、不改写既有四类攻击取证结论。
> 取证三样沿用同目录 `week11_attack_evidence.md` 口径：请求（方法 + URL + 关键头体）、响应（HTTP 状态码 + 响应体）、库内数据（执行前后 SELECT 结果）。
> XSS 另加第四样：**回显页面上下文判定**（载荷是否被当作脚本执行、是否作为文本可见）。
>
> **执行前提**：本机回环 `localhost`，未对公网或他人系统发起请求；JDK 17 / MySQL 8 / Node 18+ / Chromium 内核浏览器；
> 账号 `admin`（建会议室、建用户、查日志）与 `user1`（提交预约、查自己的预约），口令见根 README。
> 执行时间 2026-11-20；测试后数据已恢复，见文末。

## 1. 静态审计：不存在 HTML 渲染入口

全仓搜索以下危险 sink，**零命中**：

`v-html`、`innerHTML`、`outerHTML`、`document.write`、`eval(`、`new Function(`、
`dangerouslyUseHTMLString`、`insertAdjacentHTML`、`window.open` 拼接、`:href`/`javascript:` 动态绑定、模板字符串拼 HTML。

- 用户可控文本（会议室名称/位置/设备/备注、预约主题/联系人/用途、用户姓名/电话、取消原因、日志快照）回显一律走
  Vue 3 的 `{{ }}` 插值、`el-table-column prop` 或 `v-model` 回填，均自动 HTML 转义。
- 8 个控制器均为 `@RestController`，统一返回 `ApiResponse`；全局异常处理也统一 JSON。
- 实测响应头：`Content-Type: application/json`（无 `text/html` 响应）。

**这一步决定结论**：缺少「HTML 渲染」这个必要条件，注入的载荷不会被浏览器解析为脚本。

## 2. 注入与回显取证

### E-1 会议室名称 / 位置 / 设备（管理员界面真实输入）

- **请求**：`POST http://localhost:8080/api/admin/rooms`
  - 请求体：`{"name":"<img src=x onerror=\"window.__XSS_NAME__=1\">","location":"<svg onload=window.__XSS_LOC__=1>","capacity":10,"equipment":"<script>window.__XSS_EQ__=1</script>","remark":"\"><img src=x onerror=window.__XSS_REMARK__=1>","status":"ENABLED"}`
  - 路径：管理员登录 →「会议室管理」→「新增会议室」→ 填表 → 保存（非直接调接口）
- **响应**：HTTP `200`，`code=200`，列表刷新后出现新行
- **库内数据**：`SELECT id,name,location,equipment FROM meeting_room WHERE id=4`
  → `name` 原样为 `<img src=x onerror="window.__XSS_NAME__=1">`，`location`、`equipment` 同样原样
  （说明存储层不改写输入；转义发生在渲染层，这是正确分工）
- **页面判定**：重新打开 `/admin/rooms` 后执行页面上下文检查，输出：
  `{"fired":{"name":false,"loc":false,"eq":false,"remark":false},"payloadVisible":true}`
  → 四个执行标记全为 `false`，载荷以**文本节点**原样显示

### E-2 用户真实姓名

- **请求**：`POST http://localhost:8080/api/admin/users`
  - 请求体：`{"username":"xsstest01","password":"…","realName":"<img src=x onerror=window.__XSS_REAL__=1>","phone":"13800009999","role":"USER"}`
- **响应**：HTTP `200`，响应体
  `{"code":200,"message":"ok","data":{"id":4,"username":"xsstest01","realName":"<img src=x onerror=window.__XSS_REAL__=1>",...}}`
- **库内数据**：`user.real_name` 原样存有该载荷
- **页面判定**：`/admin/users` → `{"fired":{"real":false},"visible":true}`；
  `/admin/logs` → `{"fired":{"real":false,"name":false,"loc":false,"eq":false},"visible":{"real":true,"name":true}}`
  → 两页均未执行，日志里的 JSON 快照也以文本显示

### E-3 预约主题 / 联系人 / 用途

- **请求**：`POST http://localhost:8080/api/bookings`（`user1` 令牌）
  - 请求体：`{"roomId":1,"date":"2026-12-10","startTime":"2026-12-10 09:00:00","endTime":"2026-12-10 10:00:00","topic":"<img src=x onerror=window.__XSS_TOPIC__=1>","attendeeCount":3,"contact":"<svg onload=window.__XSS_CONTACT__=1>","purpose":"</textarea><script>window.__XSS_PURPOSE__=1</script>"}`
- **响应**：HTTP `200`，响应体 `{"code":200,"message":"ok","data":{"id":82,"reservationNo":"MR20261210082","status":"PENDING"}}`
- **库内数据**：`reservation.topic` 原样为上述载荷（说明 `purpose` 中的 `</textarea>` 未破坏存储结构）
- **页面判定**：`/bookings/mine` → `{"fired":{"topic":false,"contact":false,"purpose":false},"visible":{"topic":true}}`
  → 主题原样显示、未执行；联系人列本页不展示，故 `visible=false` 属预期

### E-4 变体绕过尝试

- **请求**：`POST http://localhost:8080/api/admin/rooms` 连续四次，名称分别为
  `<IMG SRC=x ONERROR=window.__X2__=1>`、`<svg/onload=window.__X3__=1>`、
  `&lt;img src=x onerror=window.__X5__=1&gt;`、`{{7*7}}`
- **响应**：四次均 HTTP `200` / `code=200`（批量结果 `[200,200,200,200]`）
- **库内数据**：四条名称原样落库
- **页面判定**：`/admin/rooms` →
  `{"fired":{"x2":false,"x3":false,"x5":false},"visible":{"uppercase":true,"svgSlash":true,"entity":true,"mustache":true}}`
  → 大小写混淆、斜杠绕过、HTML 实体、模板字面量全部未执行且原样显示；`{{7*7}}` 未被前端二次编译

## 3. 判定

| 项 | 结果 |
|---|---|
| 载荷种类 | 12 类（img onerror、svg onload、script、闭合引号拼接、大小写、斜杠、HTML 实体、模板字面量等） |
| 回显页面 | 7 个（`/admin/rooms`、`/admin/users`、`/admin/logs`、`/bookings/mine`、`/rooms`、`/rooms/1`、`/bookings/new`） |
| 执行标记 | **全部 `false`**，零弹窗、零脚本执行 |
| 显示形态 | 载荷作为文本节点原样可见（正确行为，不是被吞掉） |
| 结论 | **XSS 注入未执行，通过** |

**准确措辞**：本项目没有部署 XSS 过滤器，结论来自「Vue 默认转义 + 无 HTML 渲染入口」，
应说「注入未执行」，**不要说「XSS 已过滤/已拦截」**。
这与第 9 周风险台账 D3「令牌存 localStorage，有 XSS 时可被读取，本学期可接受」并不冲突：
当前不存在可执行点，窃取令牌的攻击链断在第一步。若将来引入 `v-html`、富文本编辑器或把用户内容拼进 HTML，须重新执行本检测。

**范围边界**：本文件只覆盖 XSS。重放攻击不在本期范围，未做取证；任务书四类攻击的取证仍以
`week11_attack_evidence.md` 为准。

## 4. 复现步骤

1. 按 README 启动 MySQL、后端、前端，确认 `GET /api/health` 返回 `code=200`。
2. 用 `admin` 登录 →「会议室管理」→「新增会议室」，名称填
   `<img src=x onerror="window.__XSS_PROBE__=1">`，保存后刷新列表。
3. 浏览器控制台执行 `window.__XSS_PROBE__`，预期 `undefined`；页面应能看到这串字符以文本显示。
4. 用 `user1` 登录，在「提交预约」主题填同样载荷并提交，到「我的预约」重复第 3 步。
5. 测完删除测试会议室与预约，恢复数据。

## 5. 测试数据恢复

已删除本次产生的 `reservation id=82`、`meeting_room id=4..8`、`user id=4`（`xsstest01`）、
`operation_log id>=4`。恢复后计数：`meeting_room=3`、`user=3`、`reservation=15`、`operation_log=3`，
与检测前一致；未改动表结构、源码或功能。
