# FitLog 架构文档

> 最后更新：2026-07-29 | 数据库版本：v6 | 包名：`com.example.fitlog`

## 1. 项目概述

FitLog 是一款 AI 驱动的原生 Android 健身记录应用，面向个人使用与作品集展示。核心能力：训练日志管理、训练计划编排、AI 教练建议、统计分析、Markdown 文件导入。

## 2. 技术栈

| 层次 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 Expressive |
| 导航 | Navigation3（`rememberNavBackStack` + `NavDisplay`） |
| 架构模式 | MVVM（ViewModel + StateFlow + Compose 收集） |
| DI | Hilt（`@HiltViewModel` / `@AndroidEntryPoint`） |
| 本地存储 | Room（v6）+ DataStore Preferences |
| 网络 | Retrofit2 + OkHttp + kotlinx.serialization |
| 异步 | Kotlin Coroutines + Flow |
| 加密 | Android Keystore（AES-GCM，API Key 加密） |

## 3. 包结构

```
com.example.fitlog/
├── FitLogApplication.kt          # @HiltAndroidApp 入口
├── MainActivity.kt               # Navigation3 宿主 + 主题驱动
├── MainViewModel.kt              # Splash 放行 + 种子触发
│
├── data/                         # 数据层
│   ├── local/                    # Room 数据库
│   │   ├── AppDatabase.kt        # 数据库入口（9 张表，v6）
│   │   ├── dao/                  # 8 个 DAO 接口
│   │   ├── entity/               # 实体（workout/ plan/ 子包）
│   │   ├── relation/             # @Relation 级联包装器
│   │   ├── ExerciseConverters.kt # 枚举/列表 TypeConverter
│   │   ├── LocalDateConverters.kt
│   │   └── PlanConverters.kt     # PlannedExerciseItem JSON 列
│   ├── remote/                   # 网络层
│   │   ├── AIApi.kt              # Retrofit 接口（动态 @Url）
│   │   └── dto/                  # 请求/响应 DTO
│   ├── repository/               # 9 个仓库类
│   ├── mapper/                   # Entity ↔ Model 扩展函数
│   ├── file/                     # SAF 文件扫描 + Markdown 解析
│   └── seed/                     # 种子数据（动作库 + 预置计划）
│
├── model/                        # 领域模型（纯数据类）
│   ├── Workout.kt / Exercise.kt / WorkoutPlan.kt ...
│   ├── ai/                       # AI 相关模型（Prompt / CoachInsight / ProviderType）
│   └── user/                     # UserProfile / TrainingGoal / Gender
│
├── feature/                      # 功能模块（Route + Screen + ViewModel + UiState）
│   ├── today/                    # 主页（最复杂：多流 combine + AI 增强）
│   ├── workout/                  # 训练记录列表
│   ├── stats/                    # 统计页（图表 + 热力图 + 体重）
│   ├── chat/                     # AI 对话
│   └── aisettings/               # AI 服务商配置
│
├── ui/                           # 全局 UI
│   ├── components/               # 通用组件（卡片 / 图表 / Snackbar）
│   ├── navigation/               # NavKey 定义（@Serializable）
│   ├── settings/                 # 设置族页面
│   └── theme/                    # Material3 主题
│
├── di/                           # Hilt Modules
│   ├── DatabaseModule.kt         # Room + DataStore + DAO 提供
│   └── AIModule.kt               # Retrofit + AIApi 提供
│
└── util/security/
    └── KeystoreManager.kt        # AES-GCM 加解密
```

## 4. 分层架构

```
┌─────────────────────────────────────────────────────┐
│  UI Layer (Compose Screen)                          │
│  - 收集 StateFlow，渲染 UI                           │
│  - 持有 transient 状态（弹层显隐、动画）              │
│  - 调用 ViewModel 事件方法                           │
├─────────────────────────────────────────────────────┤
│  ViewModel Layer                                    │
│  - MutableStateFlow（表单/事件态）                   │
│  - combine / flatMapLatest 组装 uiState             │
│  - 委托纯函数 Builder 做业务计算                     │
│  - viewModelScope.launch 触发写操作                  │
├─────────────────────────────────────────────────────┤
│  Repository Layer                                   │
│  - 协调 DAO / DataStore / 网络 API                  │
│  - Entity ↔ Model 映射（mapper 扩展函数）            │
│  - 事务管理（db.withTransaction）                    │
│  - 返回 Flow（响应式）或 suspend（一次性）            │
├─────────────────────────────────────────────────────┤
│  Data Source Layer                                  │
│  - Room DAO（本地持久化）                            │
│  - DataStore Preferences（轻量偏好）                 │
│  - Retrofit AIApi（网络请求）                        │
│  - SAF ContentResolver（文件导入）                   │
└─────────────────────────────────────────────────────┘
```

## 5. 导航架构

采用 Navigation3（非 Navigation Compose 2.x）：

- 回退栈即状态：`rememberNavBackStack(TodayKey)` 跨配置更改持久化
- 导航 = 对 backStack 的增删操作
- 所有 NavKey 为 `@Serializable` data object
- 页面模式：`XxxRoute`（入口 composable）→ `XxxScreen`（纯 UI）

```
TodayKey ──→ SettingsKey ──→ ProfileKey / AppearanceKey / AISettingsKey
    │              │              DataImportKey / ReminderKey / AboutKey
    ├──→ WorkoutKey
    └──→ StatsKey
```

## 6. 状态管理模式

### 6.1 响应式数据流（主流模式）

```kotlin
// ViewModel 中：数据层 Flow → combine → stateIn → StateFlow
val uiState: StateFlow<XxxUiState> = combine(
    repo.flowA(), repo.flowB(), localEventFlow
) { a, b, event -> assemble(a, b, event) }
 .stateIn(viewModelScope, WhileSubscribed(5000), initialValue)
```

### 6.2 事件驱动（Chat 页）

```kotlin
// 无上游 Flow，MutableStateFlow 作为唯一状态源
private val _uiState = MutableStateFlow(ChatUiState())
fun send() { viewModelScope.launch { ... _uiState.update { ... } } }
```

### 6.3 表单状态归属

- **ViewModel 持有**：表单字段值（`MutableStateFlow`），参与 `combine` 组装
- **Screen 持有**：弹层显隐、下拉菜单展开等纯 UI transient 状态

## 7. 依赖注入

| Module | 作用域 | 提供 |
|--------|--------|------|
| `DatabaseModule` | Singleton | AppDatabase、8 个 DAO、DataStore |
| `AIModule` | Singleton | Retrofit（@Named("ai")）、AIApi |

Repository 和 ViewModel 通过 `@Inject constructor` 自动注入，无需显式 Module 绑定。

## 8. 关键设计决策

| 决策 | 理由 |
|------|------|
| 无 UseCase 层 | 个人项目，Repository 直接暴露给 ViewModel，减少样板 |
| 计划动作内嵌 JSON 列 | 动作清单从不跨 session 独立查询，避免第三张表 |
| DataStore 存激活 ID | "当前使用哪个"是 UI 偏好，不属于领域数据 |
| 种子门控（SeedOrchestrator） | 防首装时 UI 收集期间种子写库导致内容翻转 |
| Splash 只等外观偏好 | 种子耗时由 Today 加载条承接，不阻塞启动 |
| AI 指纹缓存 | 同一训练状态反复进 Today 页零网络请求 |
| fallbackToDestructiveMigration | 开发阶段快速迭代，不维护迁移脚本 |

## 9. 已知架构局限

1. **无 UseCase/Domain 层**：业务逻辑散落在 ViewModel 和 Repository，复杂场景（如训练完成联动计划标记）需要跨 Repository 协调
2. **无模块化**：单 app module，feature 包之间无编译隔离
3. **无分页**：`getAllWithDetails()` 全量加载，数据量大时有性能风险
4. **跨零点不刷新**：`today` 在 ViewModel 创建时固定，长时间挂起后日期不准
5. **无流式 AI 响应**：Chat 页等待完整响应，长回复体验差
6. **exportSchema = false**：无法生成迁移脚本，版本升级依赖 destructive migration
