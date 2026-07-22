---
version: "alpha"
name: FitLog
description: Material You（动态取色）设计语言。颜色不写死色值，全部引用 MaterialTheme.colorScheme 角色，运行时由壁纸种子生成。尺寸单位为 Compose dp。

colors:
  page-background: "{colorScheme.surfaceContainerLow}"
  card-background: "{colorScheme.surfaceContainerLowest}"
  topbar-expanded: "{colorScheme.surfaceContainerLow}"
  topbar-collapsed: "{colorScheme.surfaceContainer}"
  section-label: "{colorScheme.primary}"
  button-primary-bg: "{colorScheme.primary}"
  button-primary-text: "{colorScheme.onPrimary}"
  button-tonal-bg: "{colorScheme.secondaryContainer}"
  button-tonal-text: "{colorScheme.onSecondaryContainer}"
  text-subtitle: "{colorScheme.onSurfaceVariant}"
  text-error: "{colorScheme.error}"
  chip-1-bg: "{colorScheme.primaryContainer}"
  chip-1-fg: "{colorScheme.onPrimaryContainer}"
  chip-2-bg: "{colorScheme.secondaryContainer}"
  chip-2-fg: "{colorScheme.onSecondaryContainer}"
  chip-3-bg: "{colorScheme.tertiaryContainer}"
  chip-3-fg: "{colorScheme.onTertiaryContainer}"

typography:
  page-title: "{typography.LargeTopAppBar 默认}"   # 展开大标题 → 折叠小标题，自动渐变
  card-title: "{typography.titleMedium}"
  card-subtitle: "{typography.bodyMedium}"
  section-label: "{typography.titleSmall}"
  field-hint: "{typography.bodySmall}"
  field-label: "{typography.labelMedium}"
  sheet-title: "{typography.titleLarge}"

rounded:
  card: 28px          # Compose: RoundedCornerShape(28.dp)
  button: full        # stadium/药囊，M3 Button 默认形状
  icon-circle: full   # CircleShape

spacing:
  page-x: 16px        # 页面水平 padding
  section-gap: 12px   # 卡片与区块间距（Arrangement.spacedBy）
  card-padding: 20px  # 卡片内边距
  card-gap: 12px      # 卡片内部元素间距
  icon-card: 48px     # 卡片内 logo 槽位
  icon-sheet: 40px    # 弹层列表项 logo 槽位
  logo-scale: 0.7     # 品牌 logo 在槽位内的渲染比例（logo 无内边距，需缩小）

components:
  settings-card:
    backgroundColor: "{colors.card-background}"
    rounded: "{rounded.card}"
    padding: "{spacing.card-padding}"
  button-primary:
    backgroundColor: "{colors.button-primary-bg}"
    textColor: "{colors.button-primary-text}"
    rounded: "{rounded.button}"
  button-tonal:
    backgroundColor: "{colors.button-tonal-bg}"
    textColor: "{colors.button-tonal-text}"
    rounded: "{rounded.button}"
  section-label:
    textColor: "{colors.section-label}"
    typography: "{typography.section-label}"
  provider-icon:
    size: "{spacing.icon-card}"
    rounded: "{rounded.icon-circle}"
---

## Overview

FitLog 采用 **Material You（动态取色）**：不定义任何固定色板，所有颜色引用
`MaterialTheme.colorScheme` 角色，运行时由系统壁纸种子生成（`Theme.kt` 中
`dynamicColor = true`，保持不变）。设计语言对标 Google 第一方应用（Google Account
设置页）：有色背景 + 白卡 + 大圆角 + 无阴影，层级全靠 surface container 色阶的
明度差表达，不用投影。

参考实现（后续页面照此对齐）：

- `feature/aisettings/AISettingsScreen.kt` — 页面结构、组件、交互的完整范本
- `feature/aisettings/ProviderSpec.kt` — 元数据注册表模式
- `ui/theme/Theme.kt` — 动态取色入口（不要加固定配色方案）

## Colors

**唯一规则：只用 colorScheme 角色，禁止硬编码 hex。** 页面间的一致性是结构性的
（底 vs 卡 vs 托起的明度关系），色相随壁纸变化是预期行为。

- **页面背景**：`surfaceContainerLow`（浅底，微着色）
- **卡片**：`surfaceContainerLowest`（最亮档，近白）——底深卡浅，对比出层级
- **顶栏渐变**：展开 `surfaceContainerLow`（融入背景）→ 折叠 `surfaceContainer`
  （深半档，托起滚过的内容），通过 `lerp(surfaceContainerLow, surfaceContainer, titleFraction)`
  根据滚动进度平滑插值过渡
- **自适应双态顶栏（方案 A）**：根据页面内容是否超出屏高可滚动 (`scrollState.maxValue > 0`) 智能切换顶栏形态：
  - **内容可滚动（长页面）**：开启双标题范式。顶栏常驻父级板块标题（如 "Settings"），
    内容区显示页面大标题 Header（如 "AI Configuration"）。滚动时父级标题向上淡出
    （`translationY = -12.dp`），本页标题从下向上淡入顶栏（`translationY = 12.dp`）；
    滚动停止时通过 `spring(dampingRatio = LowBouncy, stiffness = MediumLow)` 自动吸附。
  - **内容不可滚动（短页面）**：顶栏直接常驻显示本页标题（如 "About"），
    自动隐藏内容区重复的大标题 Header Box，避免双重标题混淆与纵向空间浪费。
- **精准吸附**：测量大标题 Header 容器高度加上底部 `12.dp` 布局间距为 `headerHeightPx`；
  滚动停止时自动平滑吸附至 `0` 或 `headerHeightPx`，保证大标题 100% 隐藏无残留。
- **区块标签**：`primary`（截图中的"蓝色小字"效果）
- **按钮**：主操作 `primary` 实色（`Button`）；次级操作 `secondaryContainer`
  （`FilledTonalButton`）。层级：primary > tonal > 纯文字（`TextButton` 仅用于
  辅助链接类动作）
- **辅助文字**：`onSurfaceVariant`（副标题、提示、说明）
- **Tonal 圆图标**：三对 container 色轮换（primary/secondary/tertiary），
  浅色圆底 + 同色相深色图标
- **品牌 logo**：`Image` 原色渲染，**禁止 tint**；深色字形 logo 在深色主题下
  需准备反色变体（如 `openai_light`/`openai_dark`）

## Typography

全部使用 `MaterialTheme.typography` 角色，不自定义字号：

- **页面大标题**：`headlineMedium`（位于滚动内容顶部 Header Box，`padding(start = 8.dp, top = 8.dp, bottom = 4.dp)`）
- **顶栏标题**：`titleLarge`（位于 `TopAppBar` title 槽位，支持双标题 Shared Axis 位移淡化）
- **卡片标题**：`titleMedium`
- **卡片副标题/说明**：`bodyMedium` + `onSurfaceVariant`
- **区块标签**：`titleSmall` + `primary`
- **字段标签**：`labelMedium` + `onSurfaceVariant`
- **辅助/错误提示**：`bodySmall`
- **弹层标题**：`titleLarge`

## Layout

- 页面水平 padding **16dp**；卡片间、区块间距 **12dp**（`Arrangement.spacedBy`）
- 卡片内边距 **20dp**，卡内元素间距 **12dp**
- 设置类页面的标准骨架（见 AISettingsScreen / AboutScreen）：

```
Scaffold(containerColor = surfaceContainerLow, snackbarHost = { StackedSnackbarHost(hostState) })
├─ TopAppBar（pinned scrollBehavior + lerp 背景色 + 共享轴向双标题）
└─ Column(verticalScroll + imePadding + 点击空白收键盘)
   ├─ Header Box（大标题 + onSizeChanged 测量 headerHeightPx = size.height + extraSpacingPx）
   ├─ SectionLabel / Card（按业务逻辑顺序组合）
   └─ 主按钮（filled，fillMaxWidth，页面底部）
```

- **依赖链顺序**：表单区块从上到下必须与其数据依赖方向一致
  （选服务商 → 填凭据 → 选模型 → 测试 → 保存）
- 内容不超过一屏半时用 `Column + verticalScroll`；真正的长列表才用 `LazyColumn`

## Elevation & Depth

- **全面扁平**：所有卡片 `elevation = 0.dp`，禁止用阴影表达层级
- 层级只靠两种手段：surface container 色阶的明度差、顶栏折叠时的颜色渐变
- 弹层（`ModalBottomSheet`）是唯一的"上浮"元素，自带 scrim 即可

## Shapes

- **卡片**：`RoundedCornerShape(28.dp)`（GM3 大圆角签名）
- **按钮**：stadium 药囊形（M3 Button/FilledTonalButton 默认，不要改方）
- **图标容器**：`CircleShape`（tonal 圆片、头像位）
- **药丸/标签**：`CircleShape`
- 输入框保持 M3 默认（不要全局改圆角）

## Components

| 组件 | 实现 | 要点 |
|---|---|---|
| 双标题吸附顶栏 | `TopAppBar` + `pinnedScrollBehavior()` + `titleFraction` | 共享轴向位移淡化双标题；`lerp` 背景色；`snapshotFlow` + `LowBouncy` 精准弹簧吸附 |
| 堆叠 Snackbar 宿主 | `StackedSnackbarHost` + `StackedSnackbarHostState` | 倒序 Column + `fadeIn`/`slideIn` 与 `shrinkVertically(Bottom)`，支持多条 Snackbar 并行展示与消失平滑归位 |
| 区块标签 | `SectionLabel` | `titleSmall` + `primary`，左缩 4dp |
| 卡片容器 | `SettingsCard` | `surfaceContainerLowest` + 28dp + 0 阴影 + 20dp 内边距，全页面统一 |
| 服务商图标 | `ProviderIcon` | 有 `logoRes` 用品牌 logo（0.7 缩放、不 tint），否则回退 tonal 图标；**只有它认识 logo，调用方无感知** |
| 底部选择弹层 | `ModalBottomSheet` + `ListItem` 行项 | 低频选择动作的标准入口；行项做成独立 composable，将来可平移到全屏选择页；选中项打 ✓ |
| 可编辑下拉框 | `ExposedDropdownMenuBox` + `menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)` | 点输入区弹键盘输入，点箭头只展开列表不弹键盘；**手动输入永远是兜底** |
| Tonal 按钮 | `FilledTonalButton` | 次级操作；药囊形 + `secondaryContainer` 动态色 |
| 帮助链接 | `HelpLink`（`buildAnnotatedString` + `LocalUriHandler`） | 说明文字普通色，URL 用 `primary` + 下划线 |
| 一次性消息 | `StackedSnackbarHost` + `LaunchedEffect(uiState.xxx)` + `onXxxShown()` 清除 | 见架构模式 |
| 元数据注册表 | `ProviderSpec`/`ProviderSpecs` | 与具体实体绑定的 UI 元数据（显示名、默认值、字段开关）集中一处，UI 组件不认识任何具体实体 |

## Architecture Patterns（项目补充）

后续 feature 一律照此结构，与 `feature/aisettings/` 对齐：

1. **三层文件结构**：`XxxRoute`（唯一允许 `hiltViewModel()` 的地方，收集状态）
   → `XxxScreen`（无状态，只收 `uiState` + 回调，可 Preview）→ `Preview`（mock 状态）
2. **UiState 嵌套**：按关注点拆子 state（provider / apiKey / model / endpoint /
   test / ui），ViewModel 用多个 `MutableStateFlow` + `combine` 汇聚，
   `stateIn(WhileSubscribed(5000))`
3. **表单状态住 ViewModel**，不住 `remember`（旋转不丢）；Screen 本地只留纯 UI
   瞬态（弹层开关、下拉展开）
4. **回填查仓库，不读 `uiState.value`**：combine 首次发射有时延，init/事件里
   读 `uiState.value` 会拿到 initialValue（已踩过的竞态坑）。用挂起的
   `repository.getById()` 直接查
5. **一次性事件**（Snackbar 等）：状态字段 + `LaunchedEffect` 展示 +
   `onXxxShown()` 回调清除；错误用 AlertDialog 同理（`errorMessage`/`onErrorShown`）
6. **键盘三件套**：`imePadding()` 必须在 `verticalScroll()` **外层**；
   点击输入框外区域 `detectTapGestures { focusManager.clearFocus() }` 收键盘；
   Manifest 保持 `adjustResize`（edge-to-edge 下靠 imePadding 生效）
7. **网络调用用 `Result` 包裹**，错误统一进 `uiState.ui.errorMessage`；
   测试/预览类请求用"表单临时配置"，不要求先落库（见 `testConnection`/`fetchModels`）

## Do's and Don'ts

**Do**

- 颜色只用 `colorScheme` 角色，让动态取色贯穿所有新页面
- 新页面先抄 `AISettingsScreen` 骨架（顶栏/卡片/间距/按钮层级）
- 拉取类功能永远保留手动输入兜底，失败不阻塞用户
- 低频选择动作用底部弹层，高频输入保持平铺可见
- 组件只依赖 `ProviderSpec` 式元数据，不认识具体实体

**Don't**

- 不硬编码任何 hex 色值（动态取色下会突兀且不随主题）
- 不用阴影/elevation 表达层级（用色阶明度差）
- 不在子 composable 里调 `hiltViewModel()`（破坏无状态与 Preview）
- 不在 init/事件回调里读 `uiState.value` 做数据回填（时序竞态）
- 不把 `imePadding` 放在 `verticalScroll` 之后（无效）
- 不给品牌 logo 加 tint（破坏品牌色）
- `TextButton` 不用于页面主操作（主操作必须 filled/tonal 药囊按钮）
