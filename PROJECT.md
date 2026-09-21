# 项目开发记录

> 最后更新：2026-09-17

---

## 一、项目概述

**名称**：现代化个人任务管理系统  
**技术栈**：Spring Boot 3.2.5 + Vue 3 + Vite + PostgreSQL + MyBatis-Plus + Element Plus + ECharts  
**端口**：后端 `8080`，前端 `5173`  
**数据库**：`<DB_HOST>:<DB_PORT>/todo_db`（本地开发库，地址见 `application-dev.yml`）

---

## 二、项目结构

```
待办系统/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml
│   ├── src/main/java/com/todo/
│   │   ├── TodoApplication.java      # 启动类
│   │   ├── common/                   # R、ErrorCode、BizException、GlobalExceptionHandler、PageResult
│   │   ├── config/                   # WebConfig、AppProperties、AuthProperties、AuthInterceptor、DataInitializer
│   │   ├── controller/               # TaskController、QuadrantController、StatsController、CalendarController、TimelineController、TagController、ConfigController、MigrationController、AuthController
│   │   ├── dto/                      # TaskSaveRequest、TaskQueryRequest、TaskRequests、TagSaveRequest、ExportDataDTO、AuthRequests
│   │   ├── entity/                   # Task、Tag、TaskTag、TaskLog、SystemConfig、BackupRecord、SysUser
│   │   ├── mapper/                   # TaskMapper、TagMapper、TaskTagMapper（自定义SQL）、TaskLogMapper（CAST jsonb）、SystemConfigMapper、BackupRecordMapper、SysUserMapper
│   │   ├── service/                  # TaskService、TagService、TaskLogService、ConfigService、StatsService、CalendarService、TimelineService、MigrationService、AuthService
│   │   ├── support/                  # TaskStatus、StatusTransition、OverdueCalculator、QuadrantResolver、OperateType、JwtUtil、PasswordUtil、UserContext
│   │   └── vo/                       # TaskVO、TagVO、TagBriefVO、QuadrantBoardVO、StatsOverviewVO、TrendVO、CompletionStatsVO 等
│   └── src/main/resources/
│       ├── application.yml           # 主配置
│       ├── application-dev.yml       # 数据库连接
│       └── db/migration/
│           ├── V1__init_schema.sql   # 初始建表（6 张业务表）
│           └── V2__add_user_and_isolation.sql  # 用户表 + 全表 user_id 隔离
│
└── frontend/                         # Vue 3 前端
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.js
        ├── App.vue
        ├── api/                      # request.js、task.js、stats.js、tag.js、config.js、migration.js、auth.js
        ├── components/               # TaskFormDialog.vue（可复用新增/编辑弹窗）
        ├── layout/                   # MainLayout.vue（顶部导航 + 用户菜单）
        ├── router/                   # index.js（8 路由 + 登录守卫）
        ├── store/                    # theme.js、auth.js
        ├── styles/                   # index.css（Tailwind + app-card + 暗色主题）
        ├── utils/                    # constants.js、format.js
        └── views/                    # Dashboard、TaskList、Quadrant、CalendarView、Timeline、Stats、Settings、Login
```

---

## 三、数据库设计（Flyway 迁移）

### V1 — 初始建表（6 张业务表）

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `task` | 任务表 | id, title, content, start_time, due_time, finish_time, status(0/1/2/3), priority, is_important, is_urgent, sort_order, version(乐观锁), is_delete(软删除) |
| `tag` | 标签表 | id, name, color, is_delete |
| `task_tag` | 任务标签关联 | task_id + tag_id 复合主键 |
| `task_log` | 操作日志表 | task_id, operate_type(1-7), operate_desc, operate_detail(jsonb), operator |
| `system_config` | 系统配置 | config_key, config_value, config_desc |
| `backup_record` | 备份记录 | file_name, file_path, file_size, file_format |

### V2 — 用户认证 + 数据隔离

| 变更 | 说明 |
|------|------|
| 新增 `sys_user` 表 | id, username(唯一), password_hash, nickname, avatar_url, status |
| 5 张业务表加 `user_id` | task, tag, task_log, system_config, backup_record |
| 唯一索引调整 | `tag.name` → `(user_id, name)` 唯一；`system_config.config_key` → `(user_id, config_key)` 唯一 |
| 管理员初始化 | DataInitializer 启动时创建 admin/admin123 + 默认配置 |

---

## 四、后端 API 总览

### 认证（AuthController）
| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT token |
| POST | `/api/auth/register` | 注册（开放注册） |
| GET | `/api/auth/me` | 获取当前用户信息 |
| PUT | `/api/auth/profile` | 修改昵称/头像 |
| PUT | `/api/auth/password` | 修改密码 |

### 任务（TaskController）
| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 分页查询（支持筛选/排序） |
| GET | `/api/tasks/{id}` | 任务详情 |
| POST | `/api/tasks` | 创建任务 |
| PUT | `/api/tasks/{id}` | 编辑任务（乐观锁） |
| DELETE | `/api/tasks/{id}` | 软删除 |
| PATCH | `/api/tasks/{id}/status` | 状态流转 |
| PATCH | `/api/tasks/{id}/quadrant` | 象限变更（拖拽） |
| PATCH | `/api/tasks/{id}/order` | 拖拽排序 |
| POST | `/api/tasks/batch/status` | 批量状态变更 |
| POST | `/api/tasks/batch/delete` | 批量删除 |
| POST | `/api/tasks/batch/tags` | 批量标签操作 |

### 其他模块
| 控制器 | 端点前缀 | 功能 |
|--------|----------|------|
| QuadrantController | `/api/quadrant` | 四象限看板数据 |
| StatsController | `/api/stats` | 统计（概览/趋势/象限/标签/完成率） |
| CalendarController | `/api/calendar` | 日历任务 + 热力图 |
| TimelineController | `/api/timeline` | 操作日志时间线 |
| TagController | `/api/tags` | 标签 CRUD |
| ConfigController | `/api/config` | 系统配置读写 |
| MigrationController | `/api/migration` | 导出/导入/备份/恢复/清空 |

---

## 五、前端页面

| 页面 | 路由 | 核心功能 |
|------|------|----------|
| Login | `/login` | 登录/注册合一，JWT 持久化 |
| Dashboard | `/dashboard` | 5 数据卡片 + ECharts 趋势图/象限饼图 + 今日待办 + 快捷入口 |
| TaskList | `/tasks` | 筛选栏 + 表格排序 + 分页 + 新增/编辑弹窗 + 批量操作 |
| Quadrant | `/quadrant` | 2×2 象限看板 + 快速完成/编辑/删除 + 象限拖拽（含优先级二次确认） |
| CalendarView | `/calendar` | 月份切换 + 日期格子（任务数/逾期高亮）+ 侧边栏任务详情 |
| Timeline | `/timeline` | 按日期分组倒序 + 操作类型筛选 + 字段级变更详情展开 |
| Stats | `/stats` | 时间范围切换 + 5 统计卡片 + ECharts 趋势图/象限饼图/标签柱状图 |
| Settings | `/settings` | 账户信息/改密码 + 基础设置 + 标签管理 + 导出/导入/备份/恢复/数据清空 |

### 可复用组件
- **TaskFormDialog** — 新增/编辑任务弹窗，顶部悬浮按钮和各页面编辑入口共用

---

## 六、架构设计要点

### 状态机
```
待办(0)   → 进行中(1) / 已完成(2) / 已取消(3)
进行中(1) → 待办(0) / 已完成(2) / 已取消(3)
已完成(2) → 待办(0)（重新打开）
已取消(3) → 待办(0)（恢复）
```
`StatusTransition` 类强制校验合法流转路径；同状态视为幂等空操作。
状态码定义：0-待办 1-进行中 **2-已完成 3-已取消**。

### 四象限
- `is_important` + `is_urgent` 两布尔值决定象限（Q1=重要紧急、Q2=重要不紧急、Q3=紧急不重要、Q4=不重要不紧急）
- `QuadrantResolver.suggestPriority()` 建议优先级，拖拽象限不静默改优先级，返回 `suggestPriority` 由前端二次确认

### 逾期计算
- **动态计算不落库**，`OverdueCalculator` 在查询时实时判断
- `status` 只存 0/1/2/3，不额外存"已逾期"状态

### 乐观锁
- `task.version` 字段 + `@Version` 注解
- 更新时 `WHERE version = ?`，冲突返回 409

### 数据隔离（V2）
- 所有业务表加 `user_id` 列
- 所有 Service 查询强制 `.eq(xxx::getUserId, UserContext.getUserId())`
- `AuthInterceptor` 从 JWT 解析 userId 写入 ThreadLocal `UserContext`
- 新注册用户自动初始化 5 项默认配置

### 认证方案
- **零第三方依赖**：`JwtUtil`（HMAC-SHA256）、`PasswordUtil`（PBKDF2WithHmacSHA256）全部用 JDK 自带 API
- 白名单放行：`/api/auth/login`、`/api/auth/register`、Swagger 路径
- OPTIONS 预检请求直接放行

---

## 七、前端基础设施

### axios 拦截器（`api/request.js`）
- 请求拦截：自动注入 `Authorization: Bearer <token>`
- 响应拦截：成功返回 `body.data`；401 自动清 token 跳登录页

### Pinia Store
- `store/theme.js` — 主题模式（light/dark），从服务端同步配置
- `store/auth.js` — token + user 信息，localStorage 持久化

### 路由守卫
- `router.beforeEach` 检查 `localStorage.token`，无 token 跳 `/login`

### 工具模块
- `utils/constants.js` — TASK_STATUS、QUADRANTS、PRIORITY_TEXT、OPERATE_TYPE_TEXT 等
- `utils/format.js` — formatTime、formatPercent、formatRelative、formatFileSize

---

## 八、构建命令（须记牢）

### 后端编译测试
```cmd
set JAVA_HOME=D:\jdk\jdk17
set MAVEN_OPTS=-Dfile.encoding=UTF-8
cd /d D:\自己代码\待办系统\backend
mvnw.cmd -s "D:\软件\maven\apache-maven-3.6.1\conf\settings.xml" "-Dmaven.repo.local=D:\软件\maven\maven_repository" clean test
```

### 前端编译
```cmd
set PATH=D:\nodejs\nvm\v20.19.4;%PATH%
cd /d D:\自己代码\待办系统\frontend
npm run build
```

---

## 九、踩坑记录

| 坑 | 原因 | 解决 |
|----|------|------|
| 主题切换 403 | 浏览器可能用 `127.0.0.1` 而非 `localhost` 访问，CORS Origin 不匹配 | `allowedOriginPatterns` 改为 `http://localhost:*,http://127.0.0.1:*` |
| 接口报 `Invalid CORS request` | 跨域白名单在 `WebConfig` 里硬编码，只认 `localhost` / `127.0.0.1`：浏览器把 `localhost` 解析为 IPv6 后 Origin 是 `http://[::1]:5174`，两种写法都匹配不到（实测 `http://localhost:*` 不覆盖 `[::1]`）。且 `AppProperties.Cors.allowedOrigins` 虽有定义却从未被使用，改配置不生效。再经 Vite 代理转发后 Origin 与浏览器真实来源脱节，排查难度进一步放大 | ① 白名单改为 `app.cors.allowed-origin-patterns` 配置项，开发期默认 `*`（Spring 回显请求来源，实测覆盖 `localhost`、`127.0.0.1`、`[::1]`、局域网 IP）；② **移除 Vite 代理**，前端直连后端端口，Origin 恒为浏览器真实来源。生产环境经 Nginx 同源代理后收紧为具体域名。注意 `Origin: null`（`file://`）连 `*` 也不匹配 |
| Jackson 编辑弹窗串数据 | `default-property-inclusion: non_null` 导致 `Object.assign(form, task)` 时 null 键丢失 | 改为 `always` |
| 中文路径 Maven 编译报错 | `Error reading old mojo status ... Input length = 1` | 需 `mvnw clean` 清理 |
| MockMvc multipart 中文乱码 | 默认编码非 UTF-8 | 用 `StandardCharsets.UTF_8` 显式指定 |
| `PasswordUtil` 编译报错 | `InvalidKeySpecException` 未被捕获 | catch 改为 `Exception` |
| `system_config.config_key UNIQUE` | V2 加 user_id 后全局唯一约束不再适用 | V2 迁移删除旧约束，新建 `(user_id, config_key)` 唯一索引 |
| `BizException` 动态状态码 | `@ResponseStatus` 是静态的，无法动态设置 | 用 `ResponseEntity` 手动设置 HTTP 状态码 |
| 排序字段 SQL 注入 | 请求参数直接拼 ORDER BY | 白名单映射 `Map<String, SFunction>` |
| `DataInitializer` 启动失败 | 先插入默认配置、后迁移 `user_id=0` 的残留配置，两者撞 `(user_id, config_key)` 唯一索引，导致应用上下文起不来 | 调整顺序为先迁移、再补缺失的默认配置；迁移时目标账号已有同键则丢弃残留记录 |
| 时间线分页条数不符 | `includeDeleted=false` 只在内存过滤，`total` 仍含已删除任务的日志 | 过滤下推到 SQL，用 `notInSql` 子查询排除已软删任务 |

---

## 十、后续待做

1. **Nginx 生产配置文档**：`client_max_body_size 10m`、`try_files $uri $uri/ /index.html`、生产环境关 CORS
2. **`application-prod.yml`** 和打包说明文档
3. ~~**测试适配**~~：已完成，`TestAuthSupport` 通过 MockMvc 默认请求头统一注入 JWT
4. **前端打包部署**：`npm run build` 产物部署到 Nginx
