# DESIGN.md — 任务管理 · 液态玻璃材质系统

## 1. Objective

界面应读起来像一块被精密加工过的光学玻璃盖在绘图纸上：透过它能看到底下的信息与光，但材质本身有厚度、有受光、有边缘折射暗示。质量底线是「拿掉主题切换后，浅色纸面系统仍然完整」；玻璃是第三主题，不是对前两套的重写。装饰必须服务可读性与层级，不允许为了氛围牺牲任务密度。

## 2. Product Context

- **What the product does:** 个人任务管理台：清单、四象限、日历、时间线与统计。
- **Who it's for:** 每天在多个视图间切换、盯截止时间与优先级的知识工作者；状态以「扫一眼今日待办」为主，不是营销落地页访客。
- **Adjacent brands (feel like these):** Linear（克制层级）、Things 3（工具感圆润）、Apple visionOS / iOS Liquid Glass（材质光学）。
- **Distant brand (do not feel like this):** Dribbble 式 glassmorphism 营销页——紫蓝渐变球 + 无信息卡片墙，材质抢戏、内容失焦。
- **Cultural register:** technical / instrument-like。工具界面的严肃，加上光学仪器的精密，而不是「轻奢生活方式」。

## 3. Visual Foundations

### 3a. Color

沿用产品语义色，玻璃主题只改变「光如何穿过表面」：

- **Neutral scale:** `--c-paper: 234 239 247` / `--c-surface: 252 253 255` / `--c-raised: 255 255 255` / `--c-ink: 22 29 40` / `--c-graphite: 84 96 114` / `--c-faint: 134 146 164` / `--c-rule: 206 217 231`
- **Accent:** `--c-primary: 23 69 107`（普鲁士蓝）。每屏强引导仍以近黑主按钮承担，蓝只编码「进行中 / 聚焦」。
- **Semantic:** 任务状态色与浅色主题一致，避免主题切换导致语义漂移。
- **Glass optical tokens（本主题核心）:**
  - `--glass-tint: rgb(255 255 255 / 0.22)` — 玻璃体；必须够透，否则是毛玻璃塑料
  - `--glass-tint-heavy: rgb(255 255 255 / 0.55)` — 侧栏 / 弹窗
  - `--glass-edge-hi: rgb(255 255 255 / 0.92)` — 菲涅尔上缘
  - `--glass-edge-mid: rgb(255 255 255 / 0.22)` — 环带中段
  - `--glass-fringe-cool: rgb(170 214 255 / 0.45)` — 边缘冷色散
  - `--glass-fringe-warm: rgb(255 214 228 / 0.35)` — 边缘暖色散
  - `--glass-blur: 28px` / `--glass-saturate: 1.85` / `--glass-brightness: 1.08`
  - `--glass-radius: 20px`（面板）/ `22px`（浮层）/ `0`（侧栏贴边）
- **Usage rules:** 玻璃色只出现在 `html.glass` 表面；禁止把玻璃高光色当按钮或文字色。主 CTA 永不玻璃化。

### 3b. Typography

- **Display face:** IBM Plex Sans 500/600，tracking-tight 仅用于产品名与页头读数。
- **Body face:** IBM Plex Sans 400/500，14px / 1.55。
- **Fallback stack:** `system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif`。
- **Type scale:** `11 / 12 / 13 / 14 / 18 / 24 / 32`（工具密度，不为玻璃放大字号）。
- **Weight discipline:** 正文 400；标签与导航 500；标题与读数 600。禁止三层连续 semibold。

### 3c. Spacing & rhythm

- **Base unit:** 4px。
- **Spacing scale:** `4, 8, 12, 16, 20, 24, 32, 40, 48`。
- **What "generous" means:** 内容列 `max-w-[1280px]`，卡片内边距 ≥ 16px，页级上下留白 ≥ 28px。玻璃主题不加大空白来「显得高级」——密度服务扫读。

### 3d. Component seeds

- **Button:** 三种：主 CTA（近黑实心）、次级（文字 / 幽灵）、圆形图标（工具条）。玻璃主题下主 CTA 保持实心，是唯一的强引导。
- **Card / container:** `.app-card` 即玻璃面板。特征是「透 + 有厚度的镜缘」，不是圆角模糊板。
- **Iconography:** Element Plus icons，15–17px，线性；不引入 emoji 或彩色图标块。
- **Glass elevation ladder:** Ambient stage → Rail（侧栏）→ Panel（卡片）→ Float（弹层）→ Chip（输入）。五级透光与投影递增，禁止同一浓度铺满。

## 4. Accessibility

- **Text contrast:** 玻璃体降至 0.22 时，正文必须仍满足 4.5:1；不足则提高局部 tint 或加深 ink，禁止降低字号。
- **Motion:** 光斑漂移默认允许但 `prefers-reduced-motion: reduce` 时静止；位移只用 `transform`/`opacity`。
- **Focus indicators:** `2px solid rgb(var(--c-primary))`，offset 1px；玻璃底上保持同一焦点环。
- **Alt text policy:** 本主题纯材质，无信息图片；装饰层 `pointer-events: none` 且不进无障碍树。

## 5. Voice & Tone

- **Register:** technical, plain, calm。
- **Sentence rhythm:** 短句为主；说明用完整句，不用口号。
- **Words this brand uses:** 任务、截止、进行中、逾期、完成率、切换主题。
- **Words this brand refuses:** 无缝、赋能、一站式、极致体验、焕新、惊喜、解锁潜能。
- **Address:** 直接描述对象（「任务」「视图」），少用「你」。

## 6. Implementation Practices

- **Token format:** CSS 变量于 `src/styles/index.css`；Tailwind 只映射语义色，不写死玻璃色。
- **Component library:** Element Plus + 语义类 `.app-card` / `.nav-item` / `.eyebrow` / `.readout`。
- **Image treatment rules:** 无摄影。背景光场用 CSS radial-gradient；玻璃镜缘用 gradient border（padding-box / border-box），**禁止 mask-composite**（已在项目中验证不稳定）。
- **Grid system:** 无形式化 12 栅格；侧栏 196px + 流体主区，卡片按任务流堆叠。
- **Motion rules:** `cubic-bezier(0.22, 1, 0.36, 1)`，150–400ms；只动 transform / opacity / box-shadow；禁止 bounce。
- **Material stack（实现约定）:** 每层玻璃 = backdrop-filter（blur+saturate+brightness）+ 半透明 tint + 镜面 sheen 渐变 + 菲涅尔 gradient border + 双层外投影 + inset 厚度阴影。高光必须画在 background 层，不得用覆盖文字的伪元素。

## 7. Anti-Patterns

- **No frosted plastic.** 只有 blur + 实白底的是毛玻璃贴纸；没有镜缘、色散与厚度阴影就不是液态玻璃。
- **No mask-composite borders.** 项目已验证其失效会整面铺色；镜缘只用 background-clip 双层渐变边框。
- **No purple-blue glassmorphism orbs.** 背景光场必须是本产品的冷色仪器光，不是营销页色球。
- **No glass primary CTA.** 主按钮是唯一强引导，玻璃化会削平层级。
- **No uniform frosted everything.** 表格行、密文本输入保持更实的底；浓度必须随层级变化。
- **No glow-neon rims.** 色散暗示折射，不是 RGB 外发光。

## 8. Decision-Making

1. **Readability over material.** 玻璃再好看，正文对比不够就退回更实的 tint。
2. **Hierarchy over equal treatment.** Rail / Panel / Float / Chip 必须浓度与投影可辨；禁止一层参数套全部。
3. **Cross-browser floor first.** 主路径必须在 Chromium / Firefox / Safari 一致可读；Chrome-only 滤镜只能是增强，不能是观感成立的前提。
4. **Theme isolation.** light / dark 不继承玻璃特效；glass 只覆盖 `html.glass` 作用域。
5. **Instrument identity wins.** 刻度尺、等宽读数、普鲁士蓝语义优先于「更梦幻的玻璃」。

## 9. Workflow

1. 确认表面属于 elevation ladder 哪一级（Ambient / Rail / Panel / Float / Chip / Solid CTA）。
2. 取该级 tint、blur、radius、投影令牌，不手写散落 alpha。
3. 铺材质栈：backdrop → tint → sheen → gradient border → shadows。
4. 检查前景文字对比；不足则提 tint，不改字号。
5. 确认无 mask-composite、无伪元素盖字、主 CTA 仍为实心。
6. 在 light / dark / glass 三态切换下回归同一页面。
7. `prefers-reduced-motion` 下确认光场静止且无功能损失。
