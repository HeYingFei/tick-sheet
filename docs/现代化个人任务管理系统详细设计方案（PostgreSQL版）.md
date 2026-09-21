# 现代化个人任务管理系统详细设计方案（PostgreSQL 版 · 修订稿 v1.1）

> **修订说明**：本稿基于原始设计稿《现代化个人任务管理系统详细设计方案（PostgreSQL版）》修订而成。
> 主要修订：修正 PostgreSQL 建表语法错误、重排错乱章节号、消除功能重复描述，并补齐原稿缺失的领域规则、接口契约、统计口径与验收标准。全部修订点见 **附录 A**。
>
> **本稿状态**：已锁定，作为开发唯一依据。

---

## 一、项目总体概述

### 1.1 项目背景

市面上常规待办工具功能单一，多仅支持基础任务记录，缺乏**任务优先级分层、时间维度可视化、工作复盘统计**能力。为满足个人系统化工作管理需求，打造一款集任务录入、四象限优先级分类、日历视图、时间线追溯、数据统计复盘于一体的现代化任务管理系统，支持数据持久化存储、多视图切换、工作效率分析，适配日常办公、学习任务管理场景。

### 1.2 项目定位

一款轻量化、高颜值、高实用性的个人任务管理平台，基于 PostgreSQL 数据库持久化存储，核心解决任务杂乱、优先级混乱、无历史记录、无法复盘统计的痛点。

### 1.3 核心特色能力

- **四象限任务矩阵**：基于艾森豪威尔法则，区分重要/紧急任务，智能分类归档
- **全维度时间视图**：日历视图（日/周/月任务分布）、时间线视图（全历史任务轨迹）
- **完整任务生命周期**：待办、进行中、已完成、已取消闭环管理，逾期状态动态判定
- **数据可视化复盘**：任务完成率、逾期率、象限占比、标签分布、时间趋势图表
- **现代化 UI 交互**：极简美学设计、深浅主题、拖拽排序、动态交互反馈
- **标准化数据迁移**：JSON/Excel 双向导入导出、一键备份恢复，支持跨设备迁移

### 1.4 本稿锁定的关键决策

原稿在多处给出「二选一」或未定义，本稿明确如下，后续开发不再变更：

| 决策项 | 结论 | 说明 |
| --- | --- | --- |
| 架构模式 | **前后端分离** | Vue3 + Vite 前端，Spring Boot 后端独立服务 |
| 逾期状态 | **查询时动态计算，不落库** | `status` 只存 4 个真实状态：0 待办 / 1 进行中 / 2 已完成 / 3 已取消 |
| 标签存储 | **独立 `tag` + `task_tag` 表** | 放弃逗号分隔字符串方案，保证筛选与统计准确 |
| 时间类型 | **`TIMESTAMPTZ`** | 统一 Asia/Shanghai，避免跨时区与夏令时歧义 |
| 数据库迁移 | **Flyway** | 版本化脚本，禁止手工改表 |
| 排序持久化 | **`task.sort_order`** | 拖拽产生的顺序需要落库 |
| 备份文件 | **本地磁盘目录 + `backup_record` 表** | 补齐原稿「有 UI 无设计」的缺口 |

---

## 二、技术架构设计

### 2.1 架构选型

采用 **前后端分离架构（模式二）**，原稿的「模式一：Thymeleaf 前后端一体」归档为历史备选，本次不实现。

适用场景：长期迭代、多端适配、二次开发。

- 后端：Spring Boot + MyBatis-Plus + PostgreSQL
- 前端：Vue3 + Vite + Element Plus + Tailwind CSS
- 接口规范：RESTful API、统一返回格式、全局异常处理
- 部署方式：后端独立服务（Jar）、前端 Nginx（或 Vite 预览）静态部署

### 2.2 技术栈与版本锁定

原稿写「SpringBoot 最新稳定版」，不可落地。本稿锁定具体版本，并已对照开发机实际环境验证可行性。

**后端**

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 (LTS) | 开发机已装 `D:\jdk\jdk17`，17.0.10 |
| Maven | 3.9.x（经 Wrapper） | 开发机全局 Maven 为 3.6.1，**不满足 Spring Boot 3.x 要求的 ≥3.6.3**，故使用 `mvnw` 锁 3.9.x，不改动全局环境 |
| Spring Boot | 3.2.x | 与 JDK 17 匹配 |
| MyBatis-Plus | 3.5.7+ | 使用 `mybatis-plus-spring-boot3-starter` |
| PostgreSQL Driver | 42.7.x | |
| Flyway | 9.x（Boot 托管） | 由 Spring Boot 3.2.x 依赖管理提供，PostgreSQL 支持已内置于 `flyway-core`；`flyway-database-postgresql` 是 Flyway 10 才拆出的模块，此处不需要 |
| EasyExcel | 3.3.x | Excel 导入导出 |
| springdoc-openapi | 2.x | 接口文档 |
| Lombok | 1.18.x | |

**前端**

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Node.js | 20.19.4 | 开发机 nvm 内已存在（`D:\nodejs\nvm\v20.19.4`）。全局 Node 为 14.21.3，**过旧不支持 Vite 5**，构建时临时指向 v20 目录，不改动全局 |
| Vite | 5.x | |
| Vue | 3.4.x | |
| Element Plus | 2.7.x | |
| Tailwind CSS | 3.4.x | 配 PostCSS，不使用 v4 |
| Pinia | 2.x | 状态管理 |
| Vue Router | 4.x | |
| Axios | 1.x | 请求封装 |
| ECharts | 5.5.x | 统计图表 |
| FullCalendar | 6.x + `@fullcalendar/vue3` | 日历视图 |

### 2.3 工程结构

```
待办系统/
├── docs/                                  # 设计文档
│   └── 现代化个人任务管理系统详细设计方案（PostgreSQL版）.md
├── backend/                               # Spring Boot 后端
│   ├── mvnw / mvnw.cmd / .mvn/wrapper/    # Maven Wrapper（锁 3.9.x）
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/todo/
│       │   ├── TodoApplication.java
│       │   ├── common/                    # 统一返回体、错误码、全局异常、分页封装
│       │   ├── config/                    # MyBatis-Plus、跨域、Jackson、OpenAPI 配置
│       │   ├── controller/                # REST 控制器
│       │   ├── service/ + service/impl/   # 业务逻辑
│       │   ├── mapper/                    # MyBatis-Plus Mapper
│       │   ├── entity/                    # 数据库实体
│       │   ├── dto/                       # 请求/响应对象
│       │   ├── convert/                   # Entity <-> DTO 转换
│       │   └── support/                   # 逾期判定、象限映射、导入校验等纯逻辑工具
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml        # 本地配置（含数据库连接，加入 .gitignore）
│       │   └── db/migration/V1__init_schema.sql
│       └── test/java/com/todo/
├── frontend/                              # Vue3 前端
│   ├── package.json / vite.config.js / tailwind.config.js
│   └── src/
│       ├── main.js / App.vue
│       ├── router/                        # 路由表
│       ├── store/                         # Pinia
│       ├── api/                           # Axios 接口封装
│       ├── layout/                        # 顶部导航布局
│       ├── views/                         # 七大页面
│       ├── components/                    # 通用组件
│       └── styles/                        # Tailwind + 主题变量
└── .gitignore
```

### 2.4 核心技术优势

- **PostgreSQL**：支持复杂查询、`TIMESTAMPTZ` 精准存储、`JSONB` 变更快照、部分索引，适配任务时间统计与数据筛选场景
- **FullCalendar**：高性能日历组件，支持任务拖拽、日期映射、时间段筛选
- **ECharts**：实现四象限统计、任务趋势图、占比饼图、日历热力图
- **Tailwind CSS**：组件轻量化，页面风格统一，现代化极简美学

---

## 三、整体功能模块设计

系统分为**七大核心模块**，覆盖任务全生命周期管理 + 可视化复盘。

### 3.1 系统首页仪表盘（数据总览）

核心：一站式查看全局任务数据，快速掌握工作状态。

- 核心数据卡片：总任务数、待办数、已完成数、逾期任务数、本周完成率
- 快捷入口：新增任务、四象限视图、日历、时间线、统计中心
- 数据趋势：近 7 日任务新增/完成趋势折线图
- 快捷看板：今日待办、近期逾期提醒

### 3.2 任务基础管理模块（核心）

覆盖任务全生命周期 CRUD，支持精细化配置。

- 任务新增：标题、详细备注、截止时间、开始时间、优先级、重要/紧急属性、自定义标签
- 任务编辑：支持全字段修改、状态变更、时间调整
- 任务状态：待办、进行中、已完成、已取消（逾期为派生展示状态，见 §4.2）
- 批量操作：批量完成、批量删除、批量修改标签/优先级
- 搜索筛选：关键词搜索、状态筛选、时间筛选、标签筛选、象限筛选
- 排序：支持按创建时间/截止时间/优先级排序；四象限看板内支持手动拖拽排序并持久化

### 3.3 四象限任务模块（核心特色）

基于艾森豪威尔时间管理法则分类，支持手动调整。

**四大象限分类规则**

| 象限 | 属性 | 处理策略 | 建议优先级 |
| --- | --- | --- | --- |
| 第一象限 | 重要 + 紧急 | 立即处理 | 极高(1) |
| 第二象限 | 重要 + 不紧急 | 计划处理 | 高(2) |
| 第三象限 | 不重要 + 紧急 | 授权/简化 | 中(3) |
| 第四象限 | 不重要 + 不紧急 | 舍弃/闲置 | 低(4) |

象限与优先级的联动规则见 §4.3。

**页面功能**

- 2×2 可视化看板，任务卡片自适应布局
- 卡片拖拽：拖拽任务切换象限，自动更新 `is_important` / `is_urgent` 属性
- 卡片拖拽排序：同象限内手动排序持久化到 `sort_order`
- 各象限任务数量统计、完成率统计
- 象限快速筛选，一键查看对应分类所有任务

### 3.4 日历视图模块（时间维度管理）

可视化展示任务时间分布，精准追溯每日/每周/每月工作。

- 多视图切换：日视图、周视图、月视图
- 日历热力图：日期色块区分任务密集度、完成度
- 日期悬浮：显示当日任务数量、完成情况
- 点击日期：弹窗展示当日所有任务，支持直接新增、修改任务
- 任务时间映射：支持按截止时间或开始时间挂载到对应日期（可切换映射口径）

### 3.5 时间线视图模块（历史追溯）

全历史任务轨迹记录，支持工作复盘、周报总结。

- 时间倒序展示：任务创建、开始、完成、修改、取消全轨迹
- 时间分段：按天/周分组展示历史记录
- 状态图标区分：不同操作对应不同颜色图标
- 变更详情：展示「修改」类日志的字段级新旧值对比（数据来源见 §5.5 `operate_detail`）
- 历史检索：按时间段、操作类型检索过往任务记录
- 默认过滤已删除任务的日志，提供「包含已删除任务」开关

### 3.6 数据统计复盘模块（进阶核心）

量化工作效率，实现数据化自我管理。所有指标口径以 §4.7 为准。

- 完成率统计：日/周/月任务完成率、逾期率
- 象限占比统计：四大象限任务数量饼图、占比分析
- 任务趋势统计：周期内任务新增、完成数量变化曲线
- 标签统计：各类型标签任务占比，分析工作重心
- 数据导入导出：任务数据、日志数据一键导出备份，支持外部数据批量导入恢复

### 3.7 系统数据迁移与设置模块

> **原稿重复修订**：原稿在 3.6 节与 3.7 节重复描述了导入导出功能，本稿统一收敛到本节，3.6 节仅保留其作为「统计页底部的导出入口」。

- **数据迁移管理**：标准化导入、导出、备份、恢复、清空功能，支持全量/选择性迁移
- **主题设置**：浅色/深色模式切换
- **数据管理**：导入导出、迁移备份、清空历史数据
- **基础配置**：默认任务优先级、时间格式设置

#### 3.7.1 数据导入导出功能

为解决系统迭代、设备更换、数据迁移痛点，系统内置标准化、高兼容的数据迁移功能，支持**双向数据流转**，所有导出文件可离线保存、跨设备导入、版本复用。

- **全量数据导出**：导出全部任务数据、任务操作日志数据，格式支持 Excel（可视化查看）与 JSON（标准结构化格式，可直接回导）
- **选择性导出**：支持按时间区间、任务状态、四象限分类、标签筛选后导出指定数据
- **批量数据导入**：支持上传规范 JSON/Excel 文件，一键批量导入，自动校验格式、去重、兼容历史字段
- **导入容错机制**：导入过程自动过滤非法数据、重复数据，返回成功/失败数量与异常明细，失败条目不影响已成功数据
- **迁移兼容性**：版本迭代、部署环境更换后数据可无缝迁移复用

#### 3.7.2 备份与恢复功能（原稿缺口补齐）

原稿原型要求「历史备份列表 / 一键恢复 / 删除备份」，但未给出任何落地设计。本稿补齐：

- **备份内容**：任务表、标签表、关联表、日志表、系统配置的完整快照
- **备份格式**：JSON（主用，可完整回导）与 Excel（辅用，便于人工查看）
- **备份存储**：写入后端配置项 `app.backup.dir` 指定目录（默认 `./data/backup`），文件命名 `todo-backup-{yyyyMMddHHmmss}.{json|excel}`
- **备份记录**：每次备份在 `backup_record` 表登记文件名、路径、大小、格式、任务数、日志数、创建时间（表结构见 §5.7）
- **一键恢复**：按 `backup_record.id` 恢复。恢复采用**全量覆盖**语义，执行前强制二次确认，执行过程在单个事务内完成，失败整体回滚
- **删除备份**：同时删除数据库记录与磁盘文件；磁盘文件缺失时仅删除记录并提示
- **数据清空**：清空任务、标签、关联、日志数据，保留系统配置；需输入确认文本二次确认

**导入去重规则**

| 判定键 | 规则 |
| --- | --- |
| 首选 | 若导入数据含 `id` 且该 `id` 在库中不存在 → 按新记录导入并保留原 `id` |
| 去重 | 若 `id` 已存在 → 跳过，计入「重复」 |
| 无 `id` 时的去重键 | `title` + `due_time` 均相同 → 判为重复，跳过 |
| 校验失败 | 标题为空、时间格式非法、状态/优先级越界、`due_time < start_time` → 计入「失败」并返回原因 |

---

## 四、领域规则定义（本稿新增章节）

原稿多处只描述「有什么功能」，未定义「按什么规则算」。本章补齐，作为后端实现与前端展示的统一依据。

### 4.1 任务状态机

```
        创建
         │
         ▼
      ┌──────┐  开始   ┌────────┐  完成   ┌────────┐
      │ 0待办 │ ─────▶ │1进行中 │ ─────▶ │2已完成 │
      └──────┘         └────────┘        └────────┘
         │  │               │
         │  └──── 取消 ─────┴──────▶ ┌────────┐
         │                           │3已取消 │
         └───────── 取消 ───────────▶└────────┘
```

允许的状态流转：

| 起点 | 允许流转到 |
| --- | --- |
| 0 待办 | 1 进行中、2 已完成、3 已取消 |
| 1 进行中 | 0 待办（回退）、2 已完成、3 已取消 |
| 2 已完成 | 0 待办（重新打开） |
| 3 已取消 | 0 待办（恢复） |

**不可流转**：2 已完成 → 3 已取消；3 已取消 → 2 已完成。违反时返回错误码 `422`。

进入 `2 已完成` 时，自动写入 `finish_time = now()`；从 `2 已完成` 退出时，清空 `finish_time`。

### 4.2 逾期判定规则

**核心结论：逾期不落库。**

- `task.status` 只存 `0/1/2/3` 四个真实状态，**不存在 `status = 4`**
- 「已逾期」是**派生展示状态**，由后端在返回数据时实时计算字段 `overdue`

**判定公式**

```
overdue = (status IN (0, 1)) AND (due_time IS NOT NULL) AND (due_time < now())
```

**分类说明**

| 情形 | 分类 | 是否计入逾期率 |
| --- | --- | --- |
| `status IN (0,1)` 且 `due_time < now()` | 逾期中 | 是 |
| `status = 2` 且 `finish_time > due_time` | 逾期完成 | 是 |
| `status = 2` 且 `finish_time <= due_time` | 准时完成 | 否 |
| `status = 3` | 已取消 | 否 |
| `due_time IS NULL` | 无截止时间 | 否 |

**查询实现**：查询「逾期任务」使用 SQL 条件而非状态字段

```sql
WHERE status IN (0, 1) AND due_time IS NOT NULL AND due_time < now() AND is_delete = FALSE
```

**理由**：避免引入定时任务；应用停机期间不会产生错误数据；无需考虑「作业没跑导致逾期标记丢失」的一致性问题。

### 4.3 象限与优先级联动规则

原稿未定义二者关系，本稿明确为**正交但提供智能默认值**：

1. **新建任务时**：若用户只选了象限（重要/紧急组合）而未显式选择优先级，按 §3.3 的建议优先级映射自动填充（Q1→1、Q2→2、Q3→3、Q4→4）。
2. **用户显式选择的优先级永远优先**，不会被象限覆盖。
3. **拖拽变更象限时**：只更新 `is_important` / `is_urgent`，**不静默修改 `priority`**。接口在响应中附带 `suggestPriority` 字段提示建议值，由前端弹窗二次确认；用户确认后再单独调用优先级更新接口。
4. **反向**：修改 `priority` 不影响 `is_important` / `is_urgent`。

**理由**：优先级是用户的主观决策，静默覆盖会破坏用户已录入的意图，且不可追溯。

### 4.4 标签规则

- 标签存于独立 `tag` 表，与任务通过 `task_tag` 多对多关联
- 标签名唯一（未删除范围内），长度 ≤ 50
- 单个任务标签数量上限 20 个（超出返回 `422`）
- 标签带颜色 `color`，默认 `#3B82F6`
- 删除标签为软删除（`tag.is_delete = true`），同时清理 `task_tag` 关联；**不影响**任务本身
- 标签筛选语义：多标签筛选为 **AND**（同时包含所选全部标签），前端需明示

### 4.5 软删除与日志规则

- 任务删除为**软删除**（`task.is_delete = true`），不物理删除数据
- 软删除后，任务不再出现在列表、看板、日历、统计等任何数据视图中
- `task_log` **保留**删除记录（外键不设级联，见 §5.5），用于追溯
- 时间线视图默认**过滤**已删除任务的日志，提供「包含已删除任务」开关
- 任务标题修改后，历史日志中的标题保持当时的原文，不做回溯更新

### 4.6 时间与时区口径

| 项 | 约定 |
| --- | --- |
| 数据库字段类型 | 全部使用 `TIMESTAMPTZ` |
| 数据库连接时区 | `Asia/Shanghai` |
| API 时间格式 | ISO-8601 带偏移，如 `2026-09-17T11:11:59+08:00` |
| 前端展示格式 | 默认 `YYYY-MM-DD HH:mm`，可由 `system_config.time_format` 覆盖 |
| 日界线 | `Asia/Shanghai` 当日 00:00:00.000 ~ 23:59:59.999 |
| 周起始日 | **周一** |
| 月范围 | 自然月 1 日 ~ 月末日 |
| 时间精度 | 秒（存储毫秒级，展示到分钟） |

### 4.7 统计口径定义

原稿「本周完成率」等指标未定义分子分母，本稿明确：

设统计周期为 `[start, end)`，周期内任务集合为 `S`（不含已取消、不含已删除）：

```
S            = 周期内创建的任务 ∪ 周期内到期的任务
分母  base   = |S|
完成率        = 周期内已完成任务数 / base
逾期率        = 周期内逾期任务数（§4.2 中「逾期中」+「逾期完成」） / base
新增均值      = 周期内新增任务数 / 周期天数
核心任务占比  = 重要任务数（is_important = true） / base
```

| 指标 | 周期定义 |
| --- | --- |
| 今日 | 当日 00:00:00 ~ 次日 00:00:00 |
| 本周 | 本周一 00:00:00 ~ 下周一 00:00:00 |
| 本月 | 本月 1 日 00:00:00 ~ 次月 1 日 00:00:00 |
| 全年 | 1 月 1 日 00:00:00 ~ 次年 1 月 1 日 00:00:00 |
| 自定义 | 用户指定 `start` ~ `end`，左闭右开 |

**趋势图口径**：近 N 日（默认 7）按自然日分组，分别统计每日新增数与每日完成数（以 `finish_time` 落入日期为准）。

**空数据处理**：`base = 0` 时，完成率与逾期率返回 `null`，前端展示「—」而非 0%，避免误读。

---

## 五、数据库详细设计（PostgreSQL）

> **原稿严重问题修订**：原稿建表语句使用了 MySQL 的内联 `COMMENT` 语法（如 `id BIGSERIAL PRIMARY KEY COMMENT '任务主键ID'`），**该语法在 PostgreSQL 下会直接报错，脚本无法执行**。本稿全部改为标准 `COMMENT ON COLUMN ... IS '...'` 语句。

### 5.1 设计约定

> **实际运行环境**：本文档的目标库为 `<DB_HOST>:<DB_PORT>/todo_db`（连接信息见 `application-dev.yml`，该文件不入版本库），实际版本 **PostgreSQL 12.4**（原稿写 14+，实测 12.4 完全兼容本方案使用的全部特性）。`V1__init_schema.sql` 已在该库执行成功并通过 9 项结构验证。

- 所有表使用 `BIGSERIAL` 主键
- 所有时间字段使用 `TIMESTAMPTZ`，默认 `now()`
- `update_time` 由**触发器**自动维护（原稿仅设 `DEFAULT`，UPDATE 时不会更新，属缺陷）
- 状态、优先级等枚举字段加 `CHECK` 约束，防止脏数据
- 软删除表配合**部分索引**（`WHERE is_delete = FALSE`），兼顾正确性与性能
- 全部 DDL 由 Flyway 管理，见 §5.10
- **数据库使用边界**：应用账号 `todo` 仅被授权操作 `todo_db`，任何代码与脚本不得跨库访问

### 5.2 表清单

原稿设计 3 张表。为补齐标签与备份缺口，本稿调整为 **6 张表**：

| 表名 | 说明 | 相对原稿 |
| --- | --- | --- |
| `task` | 任务主表 | 修订（字段调整 + 约束 + 注释语法） |
| `tag` | 标签表 | **新增** |
| `task_tag` | 任务标签关联表 | **新增** |
| `task_log` | 任务操作日志表 | 修订（新增变更快照字段） |
| `system_config` | 系统配置表 | 修订（注释语法） |
| `backup_record` | 备份记录表 | **新增** |

### 5.3 任务主表 `task`

```sql
CREATE TABLE task (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL DEFAULT '',
    start_time   TIMESTAMPTZ  NULL,
    due_time     TIMESTAMPTZ  NULL,
    finish_time  TIMESTAMPTZ  NULL,
    status       SMALLINT     NOT NULL DEFAULT 0,
    is_important BOOLEAN      NOT NULL DEFAULT FALSE,
    is_urgent    BOOLEAN      NOT NULL DEFAULT FALSE,
    priority     SMALLINT     NOT NULL DEFAULT 3,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    is_delete    BOOLEAN      NOT NULL DEFAULT FALSE,
    version      INTEGER      NOT NULL DEFAULT 0,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_task_status       CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT ck_task_priority     CHECK (priority BETWEEN 1 AND 4),
    CONSTRAINT ck_task_sort_order   CHECK (sort_order >= 0),
    CONSTRAINT ck_task_time_order   CHECK (
        due_time IS NULL OR start_time IS NULL OR due_time >= start_time
    )
);

COMMENT ON TABLE  task              IS '任务主表';
COMMENT ON COLUMN task.id           IS '任务主键ID';
COMMENT ON COLUMN task.title        IS '任务标题';
COMMENT ON COLUMN task.content      IS '任务详细备注内容';
COMMENT ON COLUMN task.start_time   IS '任务开始时间';
COMMENT ON COLUMN task.due_time     IS '任务截止时间';
COMMENT ON COLUMN task.finish_time  IS '任务完成时间，进入已完成状态时写入';
COMMENT ON COLUMN task.status       IS '任务状态：0-待办，1-进行中，2-已完成，3-已取消。注：逾期不落库，为查询时派生状态';
COMMENT ON COLUMN task.is_important IS '是否重要：false-否，true-是';
COMMENT ON COLUMN task.is_urgent    IS '是否紧急：false-否，true-是';
COMMENT ON COLUMN task.priority     IS '优先级：1-极高，2-高，3-中，4-低';
COMMENT ON COLUMN task.sort_order   IS '手动拖拽排序序号，同象限内升序排列';
COMMENT ON COLUMN task.is_delete    IS '逻辑删除：false-未删除，true-已删除';
COMMENT ON COLUMN task.version      IS '乐观锁版本号，每次更新 +1';
COMMENT ON COLUMN task.create_time  IS '任务创建时间';
COMMENT ON COLUMN task.update_time  IS '任务更新时间，由触发器自动维护';
```

**相对原稿的字段变化**

| 变化 | 字段 | 原因 |
| --- | --- | --- |
| 删除 | `tag_list VARCHAR(512)` | 改为独立 `tag` + `task_tag` 表，避免 LIKE 查询与字符串统计 |
| 新增 | `sort_order INTEGER` | 原稿承诺拖拽排序，但无字段承载 |
| 新增 | `version INTEGER` | 乐观锁，编辑冲突检测 |

### 5.4 标签表 `tag` 与关联表 `task_tag`

```sql
CREATE TABLE tag (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    color       VARCHAR(20) NOT NULL DEFAULT '#3B82F6',
    is_delete   BOOLEAN     NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE  tag            IS '标签表';
COMMENT ON COLUMN tag.name       IS '标签名称，未删除范围内唯一';
COMMENT ON COLUMN tag.color      IS '标签颜色，十六进制色值';
COMMENT ON COLUMN tag.is_delete  IS '逻辑删除标记';

CREATE UNIQUE INDEX uk_tag_name ON tag (name) WHERE is_delete = FALSE;


CREATE TABLE task_tag (
    task_id BIGINT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tag(id)  ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
);

COMMENT ON TABLE  task_tag         IS '任务标签关联表';
COMMENT ON COLUMN task_tag.task_id IS '任务ID';
COMMENT ON COLUMN task_tag.tag_id  IS '标签ID';

CREATE INDEX idx_task_tag_tag ON task_tag (tag_id);
```

### 5.5 任务操作日志表 `task_log`

原稿仅 `operate_type` + `operate_desc VARCHAR(255)`，**无法支撑时间线要求的「字段级变更详情」**。本稿新增 `operate_detail JSONB` 快照字段。

```sql
CREATE TABLE task_log (
    id             BIGSERIAL    PRIMARY KEY,
    task_id        BIGINT       NOT NULL,
    operate_type   SMALLINT     NOT NULL,
    operate_desc   VARCHAR(255) NOT NULL,
    operate_detail JSONB        NULL,
    operator       VARCHAR(50)  NOT NULL DEFAULT 'local',
    operate_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_log_operate_type CHECK (operate_type BETWEEN 1 AND 7)
);

COMMENT ON TABLE  task_log                IS '任务操作日志表，时间线视图数据源';
COMMENT ON COLUMN task_log.task_id        IS '关联任务ID。刻意不设外键：任务被物理清理后日志仍需保留用于追溯';
COMMENT ON COLUMN task_log.operate_type   IS '操作类型：1-创建，2-修改，3-开始，4-完成，5-取消，6-删除，7-恢复';
COMMENT ON COLUMN task_log.operate_desc   IS '操作描述，面向用户的短语，如「将截止时间调整为 2026-09-20 18:00」';
COMMENT ON COLUMN task_log.operate_detail IS '变更快照 JSONB，形如 {"field":{"old":x,"new":y}}，用于时间线字段级对比';
COMMENT ON COLUMN task_log.operator       IS '操作者标识，个人版固定 local，为多用户预留';
COMMENT ON COLUMN task_log.operate_time   IS '操作发生时间';

CREATE INDEX idx_log_task_id      ON task_log (task_id);
CREATE INDEX idx_log_operate_time ON task_log (operate_time DESC);
CREATE INDEX idx_log_type_time    ON task_log (operate_type, operate_time DESC);
CREATE INDEX idx_log_detail_gin   ON task_log USING GIN (operate_detail);
```

**`operate_detail` 示例**

```json
{
  "due_time":  { "old": "2026-09-18T18:00:00+08:00", "new": "2026-09-20T18:00:00+08:00" },
  "priority":  { "old": 3, "new": 1 },
  "title":     { "old": "写周报", "new": "写周报并同步给组长" }
}
```

### 5.6 系统配置表 `system_config`

```sql
CREATE TABLE system_config (
    id           BIGSERIAL    PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(512) NOT NULL DEFAULT '',
    config_desc  VARCHAR(255) NOT NULL DEFAULT '',
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  system_config              IS '系统配置表';
COMMENT ON COLUMN system_config.config_key   IS '配置键名，唯一';
COMMENT ON COLUMN system_config.config_value IS '配置值';
COMMENT ON COLUMN system_config.config_desc  IS '配置描述';
COMMENT ON COLUMN system_config.update_time  IS '更新时间，由触发器自动维护';
```

**初始化数据**

```sql
INSERT INTO system_config (config_key, config_value, config_desc) VALUES
('theme_mode',       'light',            '系统主题：light-浅色，dark-深色'),
('default_priority', '3',                '默认任务优先级：1-极高，2-高，3-中，4-低'),
('time_format',      'yyyy-MM-dd HH:mm', '时间展示格式'),
('week_start',       '1',                '周起始日：1-周一，7-周日'),
('calendar_field',   'due_time',         '日历视图映射字段：due_time-截止时间，start_time-开始时间');
```

### 5.7 备份记录表 `backup_record`

```sql
CREATE TABLE backup_record (
    id          BIGSERIAL    PRIMARY KEY,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(512) NOT NULL,
    file_size   BIGINT       NOT NULL DEFAULT 0,
    file_format VARCHAR(10)  NOT NULL,
    scope_desc  VARCHAR(255) NOT NULL DEFAULT '全量',
    task_count  INTEGER      NOT NULL DEFAULT 0,
    log_count   INTEGER      NOT NULL DEFAULT 0,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_backup_format CHECK (file_format IN ('json', 'excel'))
);

COMMENT ON TABLE  backup_record             IS '备份记录表，支撑设置页的备份列表与一键恢复';
COMMENT ON COLUMN backup_record.file_name   IS '备份文件名';
COMMENT ON COLUMN backup_record.file_path   IS '备份文件磁盘绝对路径，位于 app.backup.dir 之下';
COMMENT ON COLUMN backup_record.file_size   IS '文件大小，单位字节';
COMMENT ON COLUMN backup_record.file_format IS '文件格式：json 或 excel';
COMMENT ON COLUMN backup_record.scope_desc  IS '备份范围描述，如「全量」或「状态=已完成」';
COMMENT ON COLUMN backup_record.task_count  IS '备份包含的任务条数';
COMMENT ON COLUMN backup_record.log_count   IS '备份包含的日志条数';
```

### 5.8 索引与约束汇总

```sql
-- 任务表：部分索引，软删除数据不进索引
CREATE INDEX idx_task_status      ON task (status)                    WHERE is_delete = FALSE;
CREATE INDEX idx_task_due_time    ON task (due_time)                  WHERE is_delete = FALSE;
CREATE INDEX idx_task_create_time ON task (create_time DESC)          WHERE is_delete = FALSE;
CREATE INDEX idx_task_quadrant    ON task (is_important, is_urgent)   WHERE is_delete = FALSE;
CREATE INDEX idx_task_priority    ON task (priority)                  WHERE is_delete = FALSE;
-- 逾期查询专用索引
CREATE INDEX idx_task_overdue     ON task (due_time)
    WHERE is_delete = FALSE AND status IN (0, 1);
```

**触发器：自动维护 `update_time`**

```sql
CREATE OR REPLACE FUNCTION trg_set_update_time() RETURNS trigger AS $$
BEGIN
    NEW.update_time = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_task_update_time
    BEFORE UPDATE ON task
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

CREATE TRIGGER trg_tag_update_time
    BEFORE UPDATE ON tag
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();

CREATE TRIGGER trg_config_update_time
    BEFORE UPDATE ON system_config
    FOR EACH ROW EXECUTE FUNCTION trg_set_update_time();
```

### 5.9 数据增长与维护

- `task_log` 为增长最快的表，按月增长量大时建议加分区或归档任务（MVP 不实现，仅预留）
- 备份文件保留策略：默认保留最近 **20** 份，超出时提示用户手动清理（不自动删除，避免误删用户数据）

### 5.10 迁移脚本管理

- 使用 **Flyway**（Spring Boot 3.2.5 托管版本 9.22.3），脚本目录 `backend/src/main/resources/db/migration`
- 命名规范 `V{版本号}__{描述}.sql`，如 `V1__init_schema.sql`
- **禁止手工在数据库直接改表结构**，所有变更走新增迁移脚本
- 应用启动时自动执行迁移（`spring.flyway.enabled=true`）
- **中文路径注意事项**：项目路径含中文时，Maven 增量编译的状态文件可能出现编码不一致，报 `Error reading old mojo status ... Input length = 1`。遇到时执行 `mvnw clean` 即可，不影响产物正确性

---

## 六、接口设计规范（RESTful）

> **原稿缺口修订**：原稿 §6.2 仅列出接口名称，无 URL、无入参、无出参、无错误码，无法作为开发依据。本章补齐。

### 6.1 统一返回格式

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {},
  "timestamp": 1758083519000
}
```

> 注：原稿示例中 JSON 内联了 `//` 注释，标准 JSON 解析器不接受，此处已移除。

分页响应 `data` 结构：

```json
{
  "records": [],
  "total": 128,
  "page": 1,
  "size": 20,
  "pages": 7
}
```

### 6.2 错误码表

原稿仅有 200/500 两个码，异常分支无法区分。本稿定义：

| 错误码 | 含义 | 触发场景 |
| --- | --- | --- |
| 200 | 成功 | |
| 400 | 参数校验失败 | 字段缺失、格式非法、超出长度 |
| 404 | 资源不存在 | 任务/标签/备份 ID 不存在 |
| 409 | 数据冲突 | 乐观锁版本冲突、标签名重复 |
| 413 | 文件过大 | 导入文件超过 10 MB |
| 415 | 不支持的媒体类型 | 导入文件格式不支持 |
| 422 | 业务规则不通过 | 非法状态流转、截止时间早于开始时间、标签超限 |
| 500 | 服务端异常 | 未预期异常，统一兜底 |

### 6.3 分页与查询规范

- 分页参数统一 `page`（从 1 开始，默认 1）、`size`（默认 20，最大 200）
- 排序参数统一 `sortBy`（字段名）、`sortOrder`（`asc` / `desc`）
- 时间范围参数统一 `startTime` / `endTime`，ISO-8601 格式，左闭右开
- 多值筛选参数用逗号分隔，如 `status=0,1`

### 6.4 接口契约清单

基础路径 `/api`。

**任务接口**

| 方法 | 路径 | 说明 | 关键参数 |
| --- | --- | --- | --- |
| POST | `/api/tasks` | 新增任务 | body: title(必填)、content、startTime、dueTime、priority、isImportant、isUrgent、tagIds |
| GET | `/api/tasks` | 分页查询 | keyword、status、quadrant、tagIds、startTime、endTime、sortBy、sortOrder、page、size |
| GET | `/api/tasks/{id}` | 任务详情 | 返回含 `overdue`、`suggestPriority` |
| PUT | `/api/tasks/{id}` | 全字段编辑 | body 同新增 + version（乐观锁） |
| PATCH | `/api/tasks/{id}/status` | 状态流转 | body: status，非法流转返回 422 |
| PATCH | `/api/tasks/{id}/quadrant` | 象限变更（拖拽） | body: isImportant、isUrgent；响应含 `suggestPriority` |
| PATCH | `/api/tasks/{id}/order` | 排序变更 | body: sortOrder |
| DELETE | `/api/tasks/{id}` | 软删除 | |
| POST | `/api/tasks/batch/status` | 批量状态变更 | body: ids[]、status |
| POST | `/api/tasks/batch/delete` | 批量删除 | body: ids[] |
| POST | `/api/tasks/batch/tags` | 批量修改标签 | body: ids[]、tagIds[]、mode(add/remove/replace) |

**象限接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/quadrant/board` | 四象限看板数据，返回四个象限的任务列表与统计（总数/已完成/完成率） |

**日历接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/calendar/tasks` | 按时间段查询任务，参数 `startTime`、`endTime`、`dateField`(due_time/start_time) |
| GET | `/api/calendar/heatmap` | 日历热力图数据，返回每日任务数与完成数 |

**时间线接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/timeline/logs` | 分页获取日志，参数 `startTime`、`endTime`、`operateTypes`、`includeDeleted`、`page`、`size` |

**统计接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/stats/overview` | 仪表盘核心卡片数据 |
| GET | `/api/stats/trend` | 近 N 日新增/完成趋势，参数 `days`(默认 7) |
| GET | `/api/stats/quadrant` | 象限占比统计 |
| GET | `/api/stats/tags` | 标签分布统计 |
| GET | `/api/stats/completion` | 完成率/逾期率，参数 `range`(today/week/month/year/custom) + `startTime`/`endTime` |

**标签接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/tags` | 标签列表（含各标签任务数） |
| POST | `/api/tags` | 新增标签 |
| PUT | `/api/tags/{id}` | 编辑标签 |
| DELETE | `/api/tags/{id}` | 删除标签（软删） |

**配置接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/config` | 获取全部配置 |
| PUT | `/api/config` | 批量更新配置 |

**数据迁移接口**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/migration/export` | 导出，参数 `format`(json/excel)、`status`、`quadrant`、`tagIds`、`startTime`、`endTime`、`includeLogs` |
| POST | `/api/migration/import` | 导入，multipart 文件，返回成功/重复/失败统计与明细 |
| POST | `/api/migration/backup` | 创建备份 |
| GET | `/api/migration/backups` | 备份列表 |
| POST | `/api/migration/backups/{id}/restore` | 恢复指定备份 |
| DELETE | `/api/migration/backups/{id}` | 删除备份 |
| DELETE | `/api/migration/data` | 清空业务数据 |

---

## 七、页面与原型设计

> **原稿章节号错乱修订**：原稿出现两个「五」（页面详细设计、产品原型设计），本章合并为统一的第七章。

### 7.1 全局 UI 规范

- 设计风格：极简扁平化、毛玻璃质感、渐变轻量化、动态 hover 交互
- 配色方案：主色（科技蓝 `#3B82F6`）、辅助色（橙 `#F59E0B` / 绿 `#10B981` / 红 `#EF4444`）、中性色（白/灰/黑）
- 适配规则：自适应 PC 端，响应式布局
- 交互规范：所有操作带动态反馈、弹窗过渡动画、加载状态提示

### 7.2 页面布局通用结构

所有页面统一采用「顶部导航栏 + 主体内容区」经典布局，无侧边冗余菜单：

- **顶部导航栏**：系统 Logo、功能导航 Tab（首页、任务列表、四象限看板、日历视图、时间线、数据统计、系统设置）、主题切换、数据迁移快捷按钮
- **主体内容区**：自适应铺满屏幕，留白均匀，模块卡片圆角统一、阴影统一
- **全局悬浮按钮**：固定右下角「新增任务」悬浮按钮，全局快捷创建任务

### 7.3 通用组件规范

- **卡片组件**：8px 圆角、浅阴影、hover 悬浮上浮动画、深浅主题自适应
- **按钮组件**：主按钮（科技蓝）、成功按钮（绿色）、警告按钮（橙色）、危险按钮（红色）、文本按钮（灰色）
- **标签组件**：状态彩色标签，区分待办、进行中、已完成、逾期、已取消
- **弹窗组件**：居中弹窗、轻量化遮罩、关闭按钮、确认/取消双按钮统一布局
- **空状态**：统一空图 + 文字提示，无数据、无搜索结果、无历史记录统一展示样式

### 7.4 路由与页面清单

| 路由 | 页面 | 对应接口 |
| --- | --- | --- |
| `/dashboard` | 仪表盘首页 | `/api/stats/overview`、`/api/stats/trend`、`/api/stats/quadrant` |
| `/tasks` | 任务列表页 | `/api/tasks`、`/api/tags` |
| `/quadrant` | 四象限看板页 | `/api/quadrant/board` |
| `/calendar` | 日历视图页 | `/api/calendar/tasks`、`/api/calendar/heatmap` |
| `/timeline` | 时间线视图页 | `/api/timeline/logs` |
| `/stats` | 数据统计页 | `/api/stats/*` |
| `/settings` | 系统设置与数据迁移页 | `/api/config`、`/api/migration/*` |

### 7.5 首页仪表盘

页面模块从上至下依次排列：

1. **数据统计卡片区（顶部）**：四宫格卡片布局，依次展示「总任务数、待办任务、已完成任务、本周完成率」，卡片附带小幅趋势小字提示
2. **快捷功能区**：图标快捷入口，支持一键跳转四象限、日历、时间线、数据导入导出
3. **数据可视化区**：左侧近 7 日任务趋势折线图，右侧四象限任务占比饼图
4. **今日待办快捷看板**：展示当日未完成任务、逾期提醒，支持快捷勾选完成、快捷编辑

### 7.6 任务列表页

1. **筛选操作顶部栏**：关键词搜索框、状态筛选下拉、时间筛选、标签筛选、批量操作按钮、导入/导出快捷按钮
2. **任务展示区**：支持表格/卡片双视图切换
   - 卡片视图：单任务卡片展示标题、备注、状态、优先级、时间、标签，内嵌完成、编辑、删除按钮
   - 表格视图：字段完整展示，支持单列排序、单行快捷修改状态
3. **分页组件**：底部统一分页，支持自定义每页展示数量

### 7.7 四象限看板页

页面采用**均等 2×2 网格布局**，四大象限独立卡片，色彩区分、功能独立：

1. **第一象限（红系）**：重要且紧急，顶部标注「立即处理」
2. **第二象限（蓝系）**：重要不紧急，顶部标注「重点规划」
3. **第三象限（橙系）**：紧急不重要，顶部标注「简化处理」
4. **第四象限（灰系）**：不重要不紧急，顶部标注「闲置舍弃」
5. **象限统计小字**：每个象限顶部展示「总数/已完成数/完成率」实时数据

**交互原型**

- 任务卡片支持**跨象限拖拽**，松手后调用象限变更接口，自动更新「重要/紧急」属性，实时刷新统计
- 跨象限拖拽后，若建议优先级与当前优先级不一致，弹出轻量确认条「是否将优先级同步为『极高』？」，用户确认后才调用优先级更新接口（见 §4.3）
- 同象限内支持**拖拽排序**，顺序持久化到 `sort_order`

### 7.8 日历视图页

1. **顶部控制栏**：视图切换（日/周/月）、今日快捷定位、月份切换箭头
2. **主体日历区域**：标准月历网格，日期右下角标注当日任务数量，不同状态任务用不同颜色小圆点标记
3. **悬浮交互**：鼠标悬浮日期，弹窗预览当日所有任务简要列表
4. **点击交互**：点击日期，弹出详情弹窗，支持查看、新增、编辑、完成当日任务
5. **侧边辅助栏**：固定展示今日任务清单、逾期任务提醒

### 7.9 时间线视图页

1. **顶部筛选栏**：时间段筛选、操作类型筛选（创建/完成/修改/取消）、「包含已删除任务」开关
2. **时间线主体**：纵向中轴线时间轴，按「日期分组」倒序展示，日期分组标题如「2026 年 09 月 16 日」
3. **时间节点**：左侧彩色图标标识操作类型，中间展示操作时间 + 任务标题，右侧展示操作详情；「修改」类日志支持展开查看字段级新旧值对比
4. **滚动加载**：滚动加载更多历史轨迹，无分页按钮

### 7.10 数据统计页

1. **顶部时间筛选**：本周/本月/全年/自定义时间段筛选、导出报表按钮
2. **统计卡片区**：周期完成率、逾期率、新增任务均值、核心任务占比
3. **图表展示区**：上侧任务趋势折线图，下侧象限占比饼图 + 标签统计柱状图
4. **底部数据导出区**：独立功能模块，支持导出统计报表、原始任务数据、日志数据

### 7.11 系统设置与数据迁移页

页面分两大模块：

1. **基础设置模块**：主题切换、时间格式、默认优先级、周起始日、日历映射字段配置
2. **数据迁移核心模块**
   - 全量导出区域：一键导出 JSON 备份、一键导出 Excel 报表，附带功能说明文案
   - 筛选导出区域：支持按时间、状态、象限筛选后精准导出
   - 数据导入区域：文件上传框、格式说明、导入校验提示、导入进度展示
   - 备份恢复区域：历史备份列表（来源 `backup_record`）、一键恢复、删除备份
   - 数据清空区域：需输入确认文本的二次确认弹窗，防止误删数据

### 7.12 弹窗与表单原型

**新增/编辑任务弹窗**：居中弹窗、分区表单布局，字段完整、逻辑联动

- 基础信息：任务标题（必填输入框）、任务备注（多行文本框）
- 时间配置：开始时间、截止时间选择器（校验截止时间不早于开始时间）
- 优先级配置：下拉选择（极高/高/中/低）
- 象限属性：重要/紧急组合开关，自动匹配四象限；未手选优先级时按象限填充建议值
- 标签配置：多选标签 + 快速新建标签
- 底部按钮：重置、取消、确定提交

**导入导出弹窗**

- 导出弹窗：选择导出格式、导出范围、是否包含日志数据，实时生成下载链接
- 导入弹窗：展示支持格式、上传区域、导入校验进度、导入成功/失败统计反馈

### 7.13 页面跳转逻辑

- 全局任意页面均可一键跳转所有核心功能页，无页面层级限制
- 任务卡片 → 点击弹窗编辑、拖拽移动、右键快捷操作
- 仪表盘快捷入口 → 直达对应功能页面
- 所有数据页面 → 统一跳转至设置页数据迁移模块进行备份导出

### 7.14 适配规范

- PC 端：1920×1080、1440×900、1280×720 分辨率自适应居中展示
- 所有卡片、图表、表单自动缩放，布局不错乱、功能不遮挡
- 深色/浅色主题样式一一对应，交互逻辑完全一致

---

## 八、系统扩展功能（进阶可选）

MVP 版本完成后可无缝迭代，无需重构架构：

- **多用户账号系统**：账号注册登录、独立任务数据、权限隔离（`task_log.operator` 字段已预留）
- **任务提醒通知**：浏览器消息提醒、超时预警
- **任务复盘报告**：自动生成周/月工作复盘文档
- **任务子级拆解**：主任务 + 子任务，拆解复杂工作
- **标签层级**：标签分组与父子关系

---

## 九、测试与验收标准（本稿新增章节）

原稿未定义测试策略与验收标准，无法判断「做完了没有」。本稿补齐。

### 9.1 测试分层

| 层级 | 范围 | 手段 |
| --- | --- | --- |
| 单元测试 | 纯逻辑：逾期判定、状态流转校验、象限优先级映射、导入去重与校验、统计口径计算 | JUnit 5，不依赖数据库 |
| 集成测试 | Mapper 与 SQL 正确性、Flyway 迁移可执行性 | 连接远程 PostgreSQL 的 `todo_test` schema |
| 接口测试 | 全部 REST 接口的入参校验与返回结构 | MockMvc / springdoc-openapi 手工验证 |
| 前端验收 | 页面交互与视图正确性 | 手工验收清单 |

### 9.2 关键验收用例

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| AC-01 | 新建任务只选「重要+紧急」，不选优先级 | 优先级自动填充为 1（极高） |
| AC-02 | 拖拽任务从第二象限到第一象限 | `is_important`/`is_urgent` 更新，`priority` 不变，响应含 `suggestPriority=1` |
| AC-03 | 任务 `due_time` 设为昨天，状态为待办 | 详情接口 `overdue=true`，`status` 仍为 0 |
| AC-04 | 逾期任务标记完成 | `overdue` 变 false，但逾期率统计仍计入该任务的「逾期完成」 |
| AC-05 | 已完成任务尝试「取消」 | 返回 422，状态不变 |
| AC-06 | 同一任务并发编辑 | 后提交者返回 409 版本冲突 |
| AC-07 | 导入含 3 条重复、2 条非法的文件 | 返回成功/重复/失败数量与失败原因明细，已成功数据保留 |
| AC-08 | 创建备份后恢复 | 数据回到备份时点，`backup_record` 记录可查 |
| AC-09 | 软删除任务 | 列表/看板/日历/统计均不显示；时间线默认不显示，开关打开后可见 |
| AC-10 | 统计空周期 | 完成率返回 `null`，前端展示「—」 |
| AC-11 | 多标签 AND 筛选 | 选择 A、B 两个标签，只返回同时含 A 和 B 的任务 |
| AC-12 | 深浅主题切换 | 全部页面样式正确切换，刷新后保持 |

### 9.3 验收准入门槛

- 全部接口返回结构符合 §6.1，错误码符合 §6.2
- Flyway 迁移在干净数据库可一次性执行成功
- 后端单元测试通过率 100%
- §9.2 关键用例全部通过
- 前端无控制台报错

---

## 十、开发实施流程

1. **环境准备**：确认 JDK 17、Maven Wrapper、Node 20、远程 PostgreSQL 可达
2. **工程搭建**：初始化后端 Spring Boot 工程与前端 Vue3 工程，建立目录结构
3. **数据库初始化**：编写 Flyway `V1__init_schema.sql`，在 `todo` 库 `public` schema 执行建表与初始化数据
4. **后端基础层**：统一返回体、错误码、全局异常处理、MyBatis-Plus 配置、实体与 Mapper
5. **后端业务层**：任务 CRUD、状态流转、四象限、日历、时间线、标签、统计、配置
6. **后端迁移层**：导入导出、备份恢复、数据清空
7. **前端基础层**：路由、布局、主题、Axios 封装、通用组件
8. **前端业务层**：七大页面，重点实现四象限拖拽、日历、时间线、统计图表
9. **联调测试**：接口对接、边界与异常场景（对齐 §9.2 用例）
10. **样式优化**：统一 UI 风格、响应式适配、动画交互
11. **部署上线**：后端打包为 Jar、前端构建静态资源、配置备份目录与备份策略

---

## 十一、项目优势总结

- **架构灵活**：前后端分离，接口标准化，便于长期迭代与多端扩展
- **功能完整**：覆盖市面付费待办工具核心能力，新增四象限、时间线、复盘统计特色功能
- **数据安全**：PostgreSQL 持久化，支持标准化导入导出、一键备份恢复，数据可自由迁移
- **规则清晰**：逾期判定、象限联动、统计口径全部写成明确公式，无实现歧义
- **体验优质**：现代化 UI，交互流畅，适配日常办公场景
- **扩展性强**：标签独立建模、日志快照化、多用户字段预留，支持后续功能迭代升级

---

## 附录 A：相对原稿的修订记录

| 编号 | 类型 | 位置 | 修订内容 |
| --- | --- | --- | --- |
| A-01 | **错误修正** | §5 全章 | 原稿 `COMMENT '...'` 内联注释为 MySQL 语法，PostgreSQL 下无法执行，全部改为 `COMMENT ON COLUMN` |
| A-02 | **错误修正** | §6.1 | 原稿 JSON 示例内联 `//` 注释，非法 JSON，已移除 |
| A-03 | **错误修正** | §4.1 | 原稿 `update_time` 仅设 `DEFAULT`，UPDATE 时不更新，改为触发器维护 |
| A-04 | **结构修正** | 全篇 | 原稿出现两个「五」章节，后续编号全部串位，本稿重排为连续章节 |
| A-05 | **去重** | §3.6 / §3.7 | 原稿两节重复描述导入导出功能，统一收敛到 §3.7 |
| A-06 | **决策明确** | §1.4 / §2.1 | 原稿「双架构可选」未选定，本稿锁定前后端分离 |
| A-07 | **决策明确** | §2.2 | 原稿「SpringBoot 最新稳定版」不可落地，锁定具体版本并适配开发机环境 |
| A-08 | **缺口补齐** | §4.2 | 原稿把逾期当静态枚举（`status=4`）却未定义谁负责置位，改为动态计算 |
| A-09 | **缺口补齐** | §5.3 | 原稿承诺拖拽排序但无字段，新增 `sort_order` |
| A-10 | **缺口补齐** | §5.3 | 新增 `version` 乐观锁字段 |
| A-11 | **缺口补齐** | §5.4 | 原稿标签用逗号分隔字符串，无法准确筛选与统计，改为独立 `tag` + `task_tag` 表 |
| A-12 | **缺口补齐** | §5.5 | 原稿日志表无变更快照，无法支撑时间线的字段级对比，新增 `operate_detail JSONB` |
| A-13 | **缺口补齐** | §5.7 | 原稿原型要求备份列表/恢复，但无任何设计，新增 `backup_record` 表与 §3.7.2 完整设计 |
| A-14 | **缺口补齐** | §4.3 | 原稿未定义象限与优先级关系，补充联动规则 |
| A-15 | **缺口补齐** | §4.6 | 原稿未定义时区、周起始日、时间格式，补充时间口径 |
| A-16 | **缺口补齐** | §4.7 | 原稿统计指标未定义分子分母，补充完整口径 |
| A-17 | **缺口补齐** | §6 全章 | 原稿仅有接口名称，补充完整契约清单、错误码表、分页规范 |
| A-18 | **缺口补齐** | §5.10 | 新增 Flyway 版本化管理约定 |
| A-19 | **缺口补齐** | §九 | 新增测试策略与 12 条关键验收用例 |
| A-20 | **缺口补齐** | §2.3 | 新增工程目录结构约定 |

## 附录 B：环境确认结果

| 编号 | 事项 | 状态 |
| --- | --- | --- |
| B-01 | 远程 PostgreSQL 主机地址与端口 | ✅ 已确认 `<DB_HOST>:<DB_PORT>`，TCP 可达 |
| B-02 | 数据库账号与密码 | ✅ 已配置，写入 `backend/src/main/resources/application-dev.yml`（已在 `.gitignore` 中） |
| B-03 | 数据库名 `todo_db`、schema `public` | ✅ 已确认并建表成功 |
| B-04 | 数据库实际版本 | ⚠️ PostgreSQL **12.4**，低于原稿描述的 14+，但功能无缺失 |
| B-05 | 备份文件存储目录 `app.backup.dir` | 默认 `./data/backup`，如需调整请告知 |
| B-06 | 部署形态（本机运行 / 服务器部署） | 待确认，影响 Nginx 与跨域配置 |
| B-07 | 数据库账号越权限制 | ✅ 账号仅授权 `todo_db`，代码不得跨库访问 |

> 原始设计稿保留在 `.monkeycode/uploads/` 目录未作改动，便于对照。
