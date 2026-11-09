# 智能会议室管理系统 · 第 9 周前端交付（孙若普）

本目录是第 4 组第 9 周“最小原型与基础联调”中 **孙若普（Git 账号 `Yisukis`）** 负责部分的可运行实现，对应《周推进计划_第9周》里的：

- 前端 4 个页面：登录页、会议室列表页、会议室详情页、预约表单页
- 角色菜单 `RoleMenu.vue` 与路由权限守卫
- 请求层扩展：`http.ts` 抛出的 `ApiError` 携带 `data`
- 登录态持久化：`session.ts` 改为 `localStorage` 持久化，并替换第 6 周那句已过时的注释
- 两条新增路由：`/rooms/:id`、`/bookings/new`

> 说明：交付时没有在桌面/文档目录找到第 4 组的实际 Git 仓库，因此本目录是一个**独立可运行、可拷贝合并**的工程，不是直接对组仓库的提交。合并步骤见本文最后一节。
>
> 本目录只包含孙若普负责的代码与自测材料；`docs/week09/01_接口变更记录.md`、`03_第9周联调与阶段检查记录.md`、`04_阶段检查材料.md` 分别由李知渔、李牧远负责，未在本目录中代写。

## 1. 技术栈

| 项目 | 版本/说明 |
|------|-----------|
| Vue | 3.5（`<script setup>`） |
| Vue Router | 4.5 |
| 构建 | Vite 6 |
| 语言 | TypeScript 5.7，`vue-tsc --noEmit` 严格检查 |
| 请求 | 原生 `fetch` 封装，不引 axios |
| 本地联调后端 | Node 原生 `http`，仅用于自测，不是正式后端 |

## 2. 快速启动

```bash
cd meetingroom-week09
npm install          # 安装根目录验证工具（playwright-core）
npm run setup        # 安装 frontend 依赖
npm run dev          # 同时启动 mock 后端(8080) + Vite(5173)
```

浏览器打开 `http://localhost:5173`。

mock 启动时会在控制台打印本次运行的演示账号和**随机口令**：

```
普通用户 user001 / <本次随机口令>
管理员   admin001 / <本次随机口令>
停用样例 disabled001 / <本次随机口令>
```

仓库里不写死演示口令。若需要固定口令，启动前自行设置环境变量（下面以 Git Bash 为例；不要把真实口令提交到仓库）：

```bash
MOCK_USER_PASSWORD='your-user-password' \
MOCK_ADMIN_PASSWORD='your-admin-password' \
npm run dev
```

也可以分开启动：

```bash
npm run dev:mock     # 只启动 mock 后端
npm run dev:web      # 只启动前端
```

## 3. 页面与路由

| 路由 | 页面 | 可访问角色 | 说明 |
|------|------|-----------|------|
| `/login` | 登录页 | 未登录 | 账号/口令登录；账号不存在与口令错误使用同一句提示，防账号枚举；无注册/忘记密码入口 |
| `/rooms` | 会议室列表页 | 普通用户、管理员 | 按名称/最小容量/位置筛选；显示可预约/已停用；“进详情” |
| `/rooms/:id` | 会议室详情页 | 普通用户、管理员 | 基本信息复用列表接口（D1）；按日期查已占用/空闲时段；普通用户可“去预约” |
| `/bookings/new` | 预约表单页 | 普通用户 | 会议室、主题、人数、联系人、起止时间；行内校验；冲突时展示被占用时段并保留已填内容 |
| `/bookings/mine` | 我的预约 | 普通用户 | **第 10 周页面**，本周为“建设中”占位 |
| `/admin/users` 等 | 管理入口 | 管理员 | **第 10 周页面**，本周为“建设中”占位 |
| `/403` | 无权访问 | 已登录 | 角色不匹配时跳转 |

角色规则与需求清单/角色说明一致：

- 普通用户菜单：会议室查询、我的预约、退出登录；**不渲染**用户管理、会议室管理、全部预约处理、操作日志。
- 管理员菜单：会议室查询 + 四个管理入口；**不渲染“我的预约”**。原因：角色说明的权限矩阵中管理员没有“查看我的预约”权限，管理员处理预约走“全部预约处理”。
- 管理员不能提交预约：前端路由把 `/bookings/new` 限制为 `USER`，详情页不显示预约按钮；mock 后端也会对管理员 `POST /api/bookings` 返回 403。前端隐藏不是安全边界，后端校验才是。

## 4. 关键实现说明

### 4.1 `http.ts`：错误对象携带 `data`

`ApiError` 现在有 `status`、`code`、`data` 三个字段。预约冲突时后端返回 `409`，`data.occupiedSlots` 会被原样带到页面，`BookingView` 用 `extractOccupiedSlots()` 取出并展示“以下时段已被占用”。这是 FR-10 能做出来的前提，也是周计划要求“先修请求层再写页面”的原因。

401 处理：受保护请求返回 401 时清除本地会话并派发 `auth:expired` 事件，`App.vue` 监听后跳转 `/login?reason=expired`。

### 4.2 `session.ts`：登录态持久化

- 存储键 `meetingroom.session.v1`，存 `token`、`expiresAt`、`user`、`version`。
- 默认有效期 30 分钟；后端返回 `expiresIn` 时以服务端为准。
- 读取时校验版本、字段与过期时间；过期立即清除。
- 第 6 周注释写的是“仅存内存、不要改成 sessionStorage 假装持久化”，前提是当时后端不签发令牌。第 9 周后端开始签发带有效期的 Session Token（决策 D3），持久化才成立。代码中已替换该注释，写明前提变化。

### 4.3 D1：不新增 `GET /api/rooms/{id}`

会议室详情页的基本信息复用 `GET /api/rooms` 返回的 `RoomVO` 列表，按 `id` 找到当前会议室；当日占用时段由 `GET /api/rooms/{id}/occupancy?date=YYYY-MM-DD` 提供。接口总数仍为第 7—8 周冻结的 14 条，不因本周任务书表 1 的示例新增第 15 条。

### 4.4 冲突判定

冲突判定只写在 mock 后端 `hasOverlap()` 一处：

```js
return startNew < endOld && endNew > startOld
```

半开区间 `[start, end)`，首尾相接不算冲突。前端只做“结束时间晚于开始时间、不跨天、不超过 8 小时”的表单校验，不在前端重复实现冲突判定。

## 5. 验证

已在本机执行并通过：

| 命令 | 结果 |
|------|------|
| `npm run typecheck` | 通过，无 TypeScript 错误 |
| `npm run build` | 通过，Vite 产物正常生成 |
| `npm run smoke` | **38/38** 项 API 冒烟断言通过 |
| `npm run e2e` | **46/46** 项 Edge 浏览器端到端断言通过 |

`npm run e2e` 会自己拉起 mock 与 Vite，用本机 Edge 跑完登录、筛选、详情占用时段、冲突提示、成功预约、刷新保持登录、403、登出、管理员菜单、1366/1920 分辨率适配等场景，并把截图写到 `docs/week09/自测截图/`。

完整日志：`docs/week09/验证日志/final-verification.txt`；结构化结果：`docs/week09/e2e-result.json`。

## 6. 目录结构

```
meetingroom-week09/
├─ frontend/                     # 前端工程（合并到组仓库的主体）
│  ├─ src/
│  │  ├─ api/                    # auth.ts / room.ts / booking.ts / http.ts
│  │  ├─ components/RoleMenu.vue
│  │  ├─ router/                 # 路由与守卫
│  │  ├─ session/session.ts      # 登录态持久化
│  │  ├─ types/api.ts            # 接口类型
│  │  ├─ utils/                  # 日期时间、展示文案
│  │  └─ views/                  # 四个页面 + 403/占位页
│  ├─ index.html
│  ├─ package.json
│  ├─ tsconfig.json
│  └─ vite.config.ts
├─ mock-server/server.mjs        # 仅本地联调用的 mock 后端
├─ scripts/
│  ├─ dev.mjs                    # 同时启动 mock + 前端
│  ├─ smoke.mjs                  # API 冒烟
│  └─ e2e.mjs                    # Edge 端到端 + 截图
├─ docs/week09/                  # 交付说明、API 假设、自测记录、截图、日志
├─ package.json
└─ README.md
```

## 7. 本周没做的（第 10 周范围）

- “我的预约”列表、修改、取消页面（`/bookings/mine` 目前是占位页）
- 管理员用户管理、会议室管理、全部预约处理、操作日志页面（`/admin/*` 目前是占位页）
- 预约修改/取消、管理员强制取消、操作日志、利用率统计等接口
- 正式测试用例、测试报告、界面美化（分别属于第 11、12 周）

这些“没做”在页面上显示为“建设中”，没有用假数据冒充已完成。

## 8. 已知限制与合并注意事项

1. **mock 不是正式后端**：数据在内存，重启即重置；令牌也在内存，重启后旧令牌失效。正式后端必须按第 8 周设计实现数据库、BCrypt、事务行锁与令牌失效。
2. **接口字段以 `docs/week09/API契约假设.md` 为准**：如果组仓库后端的字段名/枚举值不同，优先改 `frontend/src/types/api.ts` 与对应 `api/*.ts` 适配，不要在页面里散落兼容代码。
3. **前端路由守卫不是安全边界**：普通用户直接调管理接口必须由后端返回 403（NF-03）。mock 已按此实现，正式后端必须同样校验。
4. **管理员菜单不含“我的预约”**：这是按角色说明的权限矩阵做的取舍；如果组内决定管理员也需要查看个人预约，需要先改角色说明/需求清单，再改 `RoleMenu.vue` 与路由。
5. **不要把 mock-server 当成组仓库的正式后端提交**。如果组内同意把 mock 放进仓库，请在 README 明确标注“仅本地联调”，并确保不用于演示生产数据。
6. **演示口令随机生成**：截图与仓库中没有明文口令；需要固定口令时用环境变量注入，口令文件不要提交。

## 9. 合并到第 4 组仓库

1. 先备份/确认组仓库当前分支与工作区干净。
2. 将 `frontend/src` 下本次涉及的文件复制到组仓库对应位置；若组仓库已有 `LoginView.vue`、`RoomListView.vue`，先比对再覆盖，避免覆盖组员第 9 周的其它改动。
3. 将 `frontend/src/api/http.ts`、`session/session.ts` 的改动单独提交——它们是页面开工的前置件，便于组员回溯。
4. 确认 `frontend/.env.development` 的 `VITE_PROXY_TARGET` 指向组后端（默认 `http://localhost:8080`）。
5. 执行 `npm --prefix frontend run typecheck` 与 `npm --prefix frontend run build`，再按组后端实际契约跑一遍登录→列表→详情→预约。
6. 建议按以下粒度提交（提交信息以组内约定为准，前缀保持“第9周（孙若普）：”）：

```
第9周（孙若普）：扩展请求层 ApiError 携带 data，登录态改持久化
第9周（孙若普）：新增角色菜单与路由权限守卫
第9周（孙若普）：改造登录页与会议室列表页
第9周（孙若普）：新增会议室详情页与占用时段展示
第9周（孙若普）：新增预约表单页与冲突提示
第9周（孙若普）：补充自测脚本、截图与交付说明
```

更细的文件清单、需求追溯和取舍说明见 `docs/week09/孙若普交付说明.md`。
