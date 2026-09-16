# 面试八股补全路线图（针对 FitLog）

> 面向：本项目的作者本人。**项目大部分由 AI 编写**，未系统背过 Android 八股。
> 结论先行：**你需要做三件事，顺序不能反——先「回收项目所有权」，再把项目深度翻译成面试语言，最后补齐项目完全没覆盖的系统模块**。
>
> ⚠️ 「项目基本是 AI 写的」改变了整份路线图的前提：下文所有「项目锚点」里的代码，**目前是 AI 的理解，不是你的**。所以阶段 -1（所有权回收）是其他一切的地基——不先做完它，后面所有「指着代码讲」的答案都是空中楼阁，面试官只需要追问两层就会穿帮。

---

## 0. 先看清现状

把你已有的能力和缺口摊开，才知道时间该花在哪。判断依据是仓库实测（见各阶段锚点）。

| 主题 | 项目里的实际深度 | 面试考频 | 状态 |
|---|---|---|---|
| Room / 数据库 | 迁移 ×3、@Relation 三级联查、手写关联子查询、索引/外键、TypeConverter 防御 | 高 | ✅ 可直接讲，是王牌 |
| Coroutines / Flow | stateIn/flatMapLatest/combine/shareIn、Mutex、NonCancellable、取消纪律 | 极高 | ✅ 深，但缺原理层 |
| Compose（用） | Canvas 自绘、动画状态机、副作用 61 处、Insets/EdgeToEdge、动态取色 | 极高 | ✅ 很厚 |
| Compose（原理） | 未见系统性掌握（重组/SlotTable/快照） | 极高 | ⚠️ 半桶水 |
| 单元测试 | 59 个测试文件 / 11.5k 行，Robolectric + 手写 Fake | 中高 | ✅ 稀缺优势 |
| R8 / 打包 | 真开了 minify+shrink+optimization，有 keep 规则与重复类处置 | 中 | ✅ 稀缺优势 |
| Hilt | 5 个模块全 SingletonComponent，无自定义组件/无 @ViewModelScoped/无 @HiltWorker | 高 | ⚠️ 用过没到原理 |
| WorkManager | 唯一工作、自链、重试退避、EntryPoint 注入 | 中 | ⚠️ 无 Constraints |
| 网络 | Retrofit + 1 个拦截器，**非流式**，无缓存/重试/连接池调优 | 高 | ⚠️ 浅 |
| **四大组件** | **只有 1 个 Activity，无 Service / Receiver / Provider** | 极高 | ❌ 空白 |
| **Handler / Looper** | 全仓库零接触 | 极高 | ❌ 空白 |
| **View 体系** | **0 个 layout XML、0 处 findViewById / RecyclerView / Fragment** | 极高 | ❌ 空白 |
| **Binder / IPC** | 零接触 | 高 | ❌ 空白 |
| 性能优化 | 有 R8 与被动优化，**无 Baseline Profile / StrictMode / LeakCanary / Macrobenchmark** | 高 | ⚠️ 缺体系 |
| JVM / Java 并发 | 用了 @Volatile、@Synchronized，但无 JMM 层解释 | 高 | ⚠️ 半桶水 |
| 架构 | MVVM 分层干净，**无 UseCase 层、无模块化** | 高 | ⚠️ 缺对照知识 |
| 算法 | — | 极高 | ❓ 需独立轨道 |

**一句话诊断**：你的深度全在「Compose + 协程 + Room」这条现代栈上，广度上的四大组件、Handler、View 体系是彻底空白，而这三块恰恰是问得最多、也最容易被问穿的。

---

## 1. 核心方法：锚点法

这是本路线图的灵魂。你的最大优势是**有 25k 行真实代码**，最大的风险是「背了八股但讲不出场景」——那和没背一样。所以每个八股主题都必须挂一个项目锚点，答案永远用**三句话结构**：

1. **机制**：标准答案本体（1~2 句，别展开）。
2. **锚点**：我在项目的哪个文件里用到它、为什么这么选、踩过什么坑。（这是你区别于背题党的部分）
3. **边界**：什么情况下这个选择会失效、替代方案是什么。（这是区分中级和高级的部分）

举例，问「协程的 Mutex 和线程锁有什么区别」：

> 机制：Mutex 是协程级互斥，挂起而不是阻塞线程，不会占着线程自旋等待。
> 锚点：我在 `WorkoutViewModel` 里用 `Mutex` 串行化「改单组数据」和「结束会话」两条路径，因为这两个操作都由 UI 事件触发、跑在主线程调度器上，用 `synchronized` 会阻塞主线程，而 Mutex 会让出线程。
> 边界：Mutex 只在协程世界有效，如果临界区里要跟 Java 线程共享（比如 Keystore 的 `@Synchronized getOrCreateKey()`），那个位置就必须用锁而不是 Mutex。

### 三层追问法

每个主题准备三个版本，按面试官耐心递进：

- **1 分钟版**：结论 + 一句话理由。（用来快速答完，避免主动暴露）
- **3 分钟版**：机制 + 你的锚点。
- **追问版**：预判 2~3 层追问并准备答案。例：`WhileSubscribed(5000)` → 为什么是 5000 → 为什么不用 Eagerly → 如果上游是 DataStore 会怎样。

### 反面做法（明确不要做）

- ❌ 按八股目录顺背，不看项目。你会得到一堆无法追问的答案。
- ❌ 只背「是什么」，不背「为什么」和「为什么不选另一个」。
- ❌ 把 61 处 `LaunchedEffect`、12 处 `stateIn` 当成会用就行——面试官会挑一处问语义。

---

## 2. 全局节奏与时间分配

按 7 周（业余时间，每晚 1.5~2h + 周末 4h ≈ 每周 14h）设计。三条轨道并行：

| 轨道 | 占比 | 说明 |
|---|---|---|
| A 项目深挖 | 40% | 阶段 -1、0、1、2、5 —— 先回收所有权，再把能力转成答案 |
| B 短板补课 | 40% | 阶段 3、4 —— 补四大组件/Handler/View/性能体系 |
| C 算法 + JVM | 20% | 贯穿全程，每天固定 1h 不可挪用 |

**铁律**：轨道 C 每天必须做，因为它靠肌肉记忆，突击无效；轨道 A/B 可以按阶段集中攻。**阶段 -1 期间轨道 B/C 照常并行**，所有权回收和背题不冲突。

---

## 3. 阶段 -1：项目所有权回收（1~1.5 周）★ 一切的地基

**问题本质**：AI 写的代码在你仓库里，但不在你脑子里。面试不是考「你的仓库有什么」，是考「你能对任意一行代码的决策现场答辩」。所以第一步不是背任何八股，是**把 25k 行代码里的核心 20% 变成你自己的**。

### 方法：三遍导读法

按依赖方向读，不要按文件名字母序读。每遍的目的不同：

**第一遍：架构骨架（2~3 天）**
- [ ] 从 `MainActivity` → 导航路由（`ui/` 下的 Route）→ 各 feature 的 ViewModel → repository → DAO，沿一条数据流走通一遍：用户在 `ActiveSessionScreen` 记一组重量 → 经 `WorkoutViewModel` → `WorkoutRepository` → Room 落库 → `TodayViewModel` 怎么读到它。**能白板画出这条链路才算过。**
- [ ] 自己手画一张架构图（包依赖 + 数据流向），不看不抄 `docs/architecture.md`，画完再对差异。
- [ ] 给自己讲清三个分层规则为什么存在：feature 不 import `data.local`、domain model 与 entity 分离、`util/` 收口共享指标逻辑。

**第二遍：决策考古（4~5 天）**
项目里的每处「为什么」都是你未来的面试答案素材。逐个文件问三个问题：它解决什么问题？为什么选这个方案而不是另一个？改错会坏什么？重点考古清单（按优先级）：
- [ ] `data/local/Migrations.kt`：三个迁移各解决了什么？8→9 为什么把关联从 `SavedStateHandle` 挪到数据库列？
- [ ] `WorkoutViewModel` 里的 `Mutex` / `NonCancellable` / 手动 rethrow `CancellationException`——为什么这三处必须这么写，去掉会出什么 bug（**这是协程八股的最好教具，读懂它们 = 读懂半个协程**）。
- [ ] `AgentEngineImpl` 的 `@Volatile` + `rebuildLock`、`ChatViewModel` 的 `runJob`/`stopRun` 结构化并发。
- [ ] `OpenAiAdapters.repairDanglingToolCalls` 和 `truncateHistory` 各自的取舍。
- [ ] `proguard-rules.pro` 的条件 keep 规则 + `build.gradle.kts` 里的依赖 exclude。
- [ ] 12 处 `stateIn(WhileSubscribed(5000))` + 1 处 `Eagerly` 例外（Splash 死锁的故事）。
- [ ] 顺手完成阶段 0 里「更新 `docs/architecture.md` 使其与代码一致」——**用自己读代码的结论去改，而不是让 AI 改**，改的过程就是复习。

**第三遍：动手验证（2~3 天，最关键）**
读会了不等于会。用三件事检验所有权真的到手了：
- [ ] **关掉 AI，自己修一个 bug 或加一个小 feature**。可以从这些里挑：给某个列表加空态、修 `docs/` 里记录的已知取舍、给 `VolumeAggregator` 补一个测试用例。卡住的地方就是你的真实盲区。
- [ ] **自己写一遍 `runTest` 单元测试**：挑 `WorkoutParse` 或任何一个 ViewModel，不借助 AI 从零写一个测试文件并跑绿。
- [ ] **把某个模块讲给别人听（或录音）**：3 分钟讲 `WorkoutWithExerciseLogs` 三级联查的原理和你项目里的用法，讲不卡壳才算通过。

### 关于「AI 写的项目」怎么在面试里说

- ❌ 不要谎称每行都自己写。追问两层就穿帮，穿帮的不是「用 AI」，是「不诚实」——后者是硬伤。
- ✅ 正确姿态：「这个项目我用 AI 结对开发的，我的角色是**架构决策、需求拆解、代码审查和踩坑修复**」。前提是阶段 -1 做完之后这话是真的——你能答上每个决策的 why。
- 加分话术：「用 AI 开发反而逼我做了更多 code review 和测试（59 个测试文件），因为我不能信任我没读过的代码。」——如果你确实把导读做完了，这句就是真话，而且是 2026 年面试里非常稀缺的工程判断力信号。

**产出物**：一张手画架构图 + 一份考古笔记 + 一个自己独立完成的 commit + 3 分钟录音。

---

## 4. 阶段 0：把项目变成讲稿（2~3 天）

面试第一问永远是「介绍一下你的项目」。这题答不好，后面的八股会变成审问。

- [ ] **更新 `docs/architecture.md`**（若阶段 -1 未完成）。它已经和代码脱节：文档说 DB v6 / 9 张表 / 8 个 DAO / `exportSchema = false` / 使用破坏性迁移，实际是 v9 / 11 实体 / 10 个 DAO / `exportSchema = true` / 3 个迁移且已移除破坏性迁移。**把文档改到与代码一致，本身就是最好的复习**——你会发现有些地方自己都讲不清。
- [ ] **写一份自己的「项目 20 问」**，每题 3 句话。至少要覆盖：
  - 为什么用 Navigation3 而不是 Navigation2？`rememberNavBackStack` 的状态怎么在进程重建后恢复？
  - 为什么没有 UseCase 层？什么规模的项目才需要？
  - 为什么移除 `fallbackToDestructiveMigration()`？代价是什么？
  - 为什么把「从计划开始训练」的关联从 `SavedStateHandle` 挪到数据库列？（线索：`Migrations.kt` 的 8→9 注释）
  - 为什么 Chat 的 ViewModel 要提升到 Activity 作用域？（线索：`ChatRoute` 里 `hiltViewModel(activity)`）
  - 为什么 `MainViewModel.appearance` 用 `Eagerly` 而非 `WhileSubscribed`？（线索：Splash 的 `setKeepOnScreenCondition` 只读 `.value`，用 WhileSubscribed 会死锁）
  - 数据导入的幂等为什么最终落到唯一索引而不是先查后插？
  - 为什么 AI 请求的读超时设 180 秒？
- [ ] **准备 5 分钟项目串讲**：业务闭环（计划→执行→沉淀→回顾→洞察→调整，见 `docs/business-loop-roadmap-2026-09-02.md`）→ 技术栈 → 你最有把握的 2 个技术亮点 → 已知短板（主动说，别等人问）。
- [ ] **主动交底已知短板**：无分页（`getAllWithDetails()` 全量加载）、无模块化、无流式响应、无 Baseline Profile。自己先说出来的短板是「有判断力」，被问出来才是「有缺陷」。

**产出物**：一份能直接讲的 5 分钟稿 + 20 个有锚点的问答。

---

## 5. 阶段 1：Kotlin + 协程（1 周）

离你项目最近，收益最快。
→ 阶段 1 的重点不是「会用」，而是「原理层」。

### 要背的点

**Kotlin 语言**
- [ ] 型变：`in` / `out` / 声明处 vs 使用处型变。为什么 `List<out E>` 而 `MutableList<E>` 是不变的？
- [ ] `inline` / `reified` / `crossinline` / `noinline`：内联解决什么问题（lambda 对象分配 + 非局部返回），代价是什么（字节码膨胀）
- [ ] `sealed` 的穷尽性检查；`data class` 的坑（`copy` 浅拷贝、`componentN`、在集合里的 `hashCode`）
- [ ] 扩展函数是静态解析（无多态），与成员函数冲突时的优先级
- [ ] 委托：`by lazy` 的线程模式、`by viewModels()`、属性委托原理
- [ ] 作用域函数 `let/run/with/apply/also` 的选择依据

**协程（超高频）**
- [ ] 挂起函数原理：CPS 变换 + 状态机 + `Continuation`。能画出「一个挂起两次的函数被编译器改成什么样」
- [ ] 结构化并发：父子 Job 关系、取消如何传播、为什么 `GlobalScope` 是坏的
- [ ] `CoroutineScope` / `Job` / `CoroutineContext` 的关系；`SupervisorJob` 与 `Job` 的区别（一个子协程失败是否影响兄弟）
- [ ] 调度器：`Main` / `IO` / `Default` 的线程池模型，`Dispatchers.IO` 为什么是弹性 64 线程
- [ ] 异常处理：`CoroutineExceptionHandler` 生效范围（只在 launch 根协程生效）、`try/catch` 与 `SupervisorJob` 的配合
- [ ] 取消是协作式的：为什么 `finally` 里要 `withContext(NonCancellable)` 才能挂起
- [ ] Flow：冷流 vs 热流、`SharedFlow` / `StateFlow` 区别（replay / 去重 / 初始值）、`stateIn` 三种 `SharingStarted` 的真实语义
- [ ] Flow 操作符：`flatMapLatest` 的取消语义、`combine` vs `zip` 的区别（一个取最新、一个配对）、`distinctUntilChangedBy`
- [ ] **你没写过但一定会被问**：`Channel` / `callbackFlow` / `debounce` / `Flow` 的背压

### 项目锚点（直接指着代码讲）

| 八股点 | 锚点文件 | 可讲的点 |
|---|---|---|
| Mutex 挂起不阻塞 | `feature/workout/WorkoutViewModel.kt` | 串行化 updateSet / finishSession |
| NonCancellable | 同上 `finishSession` | 会话结束必须落库，VM 销毁不能取消写 |
| 取消不能被 runCatching 吞 | `feature/agent/engine/AgentEngineImpl.kt`、`feature/chat/ChatViewModel.kt` | 手动 rethrow `CancellationException` |
| @Volatile + Mutex | `AgentEngineImpl.kt` `rebuildLock` / `currentRunner` | 可见性 vs 互斥是两件事 |
| flatMapLatest 取消语义 | `data/repository/AIProviderConfigRepository.kt` `activeProvider` | 切 provider 时旧请求自动取消 |
| shareIn 去重 | `feature/today/TodayViewModel.kt` `sharedMaterials` | 避免 N 处重复订阅 Room |
| WhileSubscribed(5000) | 12 处 `stateIn`，含 1 处 `Eagerly` 例外 | 为什么要留 5000ms、例外为什么必须 Eagerly |
| 冷流每订阅一次跑一次 | `WorkoutDao` 的 `@Query` Flow | 解释 shareIn 的价值 |
| 结构化并发 | `ChatViewModel.runJob` + `stopRun()` | 取消整个 Agent 运行 |
| 定时器 + 冻结计时 | `ChatViewModel` 的 1s ticker | `SystemClock.elapsedRealtime()` 而非 `currentTimeMillis` |

### 产出物

- [ ] 一份「协程原理」手写笔记：CPS 状态机图 + 结构化并发取消树图（必须手画一遍，画得出才算懂）
- [ ] 一个独立小 demo：用 `callbackFlow` 包装一个回调式 API、用 `debounce` 做搜索、用 `Channel` 做事件流。这三个项目里没有，必须补

---

## 6. 阶段 2：Compose 原理（1.5 周）

你项目里 Compose 代码量最大、最独特（自绘图表 + 动画状态机），但**会用 ≠ 懂原理**。这块的回报极高：你能用别人的八股答案，加上自己的真实调试经历。

### 要背的点

- [ ] **重组机制**：什么触发重组、重组的最小单位（不是函数，是「读取了变化状态的最近可重组作用域」）、为什么 `Composable` 函数会被跳过
- [ ] **三阶段**：组合 → 布局 → 绘制，以及「只读状态不触发重组」的原理。**这是你项目的现成王牌**（见下）
- [ ] **SlotTable**：Compose 怎么记录状态位置、「位置记忆」与 `key` 的关系、`remember` 为什么依赖调用位置
- [ ] **快照系统 Snapshot**：`mutableStateOf` 的读写追踪、全局快照 / 可变更快照、`Snapshot.observe`
- [ ] **稳定性推断**：`@Stable` / `@Immutable` 的实际作用、`List` 为什么是不稳定类型、`data class` 全 `val` 是否自动稳定
- [ ] `remember` vs `rememberSaveable` vs `derivedStateOf` 的语义差异（`derivedStateOf` 解决的是「读状态导致的额外重组」，不是性能万能药）
- [ ] 副作用 API 的语义边界：`LaunchedEffect` / `DisposableEffect` / `SideEffect` / `rememberUpdatedState` / `produceState` 各自「什么时候执行、什么时候重跑、key 变了怎么办」
- [ ] **Modifier 原理**：为什么 Modifier 的顺序敏感、`Modifier.Node` 与已废弃的 `Modifier.composed`
- [ ] `LazyList` 复用机制、`key` 的作用与代价、`contentType`
- [ ] Compose 与 View 互操作（`AndroidView` / `ComposeView`）
- [ ] 重组的性能诊断手段：Layout Inspector 的 recomposition counts、编译器指标

### 项目锚点

| 八股点 | 锚点 | 可讲的点 |
|---|---|---|
| 只在绘制阶段读状态 | `ui/components/chart/AnimatedChartState.kt` | 注释明确写了「所有动画值只允许在 draw 作用域读取（逐帧仅触发重绘，不触发重组）」——这是三阶段理论的实证 |
| Canvas 自绘 | `AnimatedBarChart.kt`、`MiniLineChart.kt`、`MetricCard.kt` | `Path` / `drawArc` / `rememberTextMeasurer` / 虚线目标线 |
| 手势命中测试 | `ContributionHeatmap.kt` | 53 周网格 + `detectTapGestures` 把点击坐标反解成日期 |
| derivedStateOf 的正确用法 | `CollapsingTitleScaffold.kt` | 折叠标题的 alpha/translationY 由滚动比例驱动，避免逐帧重组 |
| rememberUpdatedState | 5 处使用 | 长生命周期 effect 里读最新回调 |
| 副作用语义 | 61 处 `LaunchedEffect` | 挑 3 处讲 key 的设计 |
| 动画 API 谱系 | 全项目 | `Animatable` / `animateFloatAsState` / `rememberInfiniteTransition` / `AnimatedContent` / `MutableTransitionState` 的选择依据 |
| 自定义动画状态机 | `AnimatedChartState.kt` | 三向 diff（MATCHED/ENTER/EXIT）+ 错峰入场，比「用 AnimatedVisibility」高一个段位 |
| Insets / EdgeToEdge | `MainActivity` + 各页面 | 根 Scaffold 置零 + 逐页 `imePadding()`/`navigationBarsPadding()` 的取舍 |
| 主题与动态取色 | `ui/theme/Theme.kt` | API 31+ 动态取色 + `CompositionLocal` 注入自定义语义色 |
| 「待补」 | 无 `BoxWithConstraints` / `WindowSizeClass` | 自适应布局是空白，概念必须补 |

### 实践任务（把八股变成作品）

- [ ] **用 Layout Inspector 实测**你的图表动画：确认动画帧确实没有重组（只有重绘）。这个实测数据是面试里的杀伤性证据。
- [ ] **加一个 Baseline Profile** 并用 Macrobenchmark 测启动耗时。项目现在完全没有（无 `baseline-prof.txt`、无 profileinstaller、无 BaselineProfile 插件），做了就是可量化的性能优化经历。
- [ ] 把 `AnimatedChartState` 的设计写成一篇短文（放 `docs/`）。能写清楚 = 能讲清楚。

---

## 7. 阶段 3：Android 本体四件套（2 周）★ 最大缺口

这块项目里**一行都没有**，必须专门造 demo 学。建议新建一个独立的小工程（**不要污染 FitLog**），做一个「带通知的下载器 + 列表页」，能同时覆盖四大组件、Handler、View 体系。

### 3.1 四大组件（你只有 Activity）

- [ ] **Activity**：完整生命周期（含 `onSaveInstanceState` / `onRestoreInstanceState` 时机）、配置变更与重建、`launchMode` 四种的真实差异（尤其 `singleTask` / `singleInstance` 与 `taskAffinity` 的组合）、`onNewIntent`、`FLAG_ACTIVITY_*`、`enableOnBackInvokedCallback`（你 manifest 里有，可以讲 13+ 的预测式返回）
- [ ] **Service**：`startService` vs `bindService` 生命周期差异、前台服务与 `startForeground` 的 5 秒限制、Android 12+ 前台服务启动限制、`IntentService` 已废弃用什么替代、`JobIntentService`
- [ ] **BroadcastReceiver**：静态注册 vs 动态注册、8.0 隐式广播限制、有序广播与 `abortBroadcast`、`LocalBroadcastManager` 为什么不推荐
- [ ] **ContentProvider**：`onCreate` 在 `Application.onCreate` **之前**执行的启动顺序陷阱、`ContentResolver` 与 URI 权限、`FileProvider` 做文件共享。**你其实已经用了 `ContentResolver`**（`data/file/MarkdownFileScanner.kt` 走 SAF 树 URI、导出用 `CreateDocument`），只是没写 Provider——把这半边的经验接上概念即可
- [ ] **Intent / IntentFilter**：显式隐式、`exported` 的安全含义（你 manifest 里 `exported="true"` 是必须的，可以讲为什么）
- [ ] **进程优先级与 LMK**：前台/可见/服务/后台/空进程，什么情况下被杀

### 3.2 Handler / Looper / 消息机制（超高频）

- [ ] `Looper.prepare` / `loop` / `MessageQueue.next` 的完整链路
- [ ] 主线程 Looper 谁创建的？`ActivityThread.main` 里做了什么？
- [ ] `Handler` 内存泄漏的真正原因（不是「内部类持有 Activity」，是 MessageQueue 持有 Message 持有 Handler 持有 Activity），以及为什么现在用静态类 + WeakReference
- [ ] `ThreadLocal` 在 Looper 中的用法（每线程一个 Looper）
- [ ] **同步屏障**（sync barrier）与异步消息：`ViewRootImpl` 的 `scheduleTraversals` 怎么保证 VSYNC 优先
- [ ] `IdleHandler` 的用途
- [ ] `Choreographer` 与 VSYNC、丢帧判定、`FrameCallback`
- [ ] `Handler.postDelayed` 的精度问题与替代（`Choreographer` / `SystemClock`）

### 3.3 View 体系（你完全空白）

- [ ] **绘制流程**：`measure` → `layout` → `draw`，`MeasureSpec` 的三种模式与 `ViewGroup` 的测量规则、`getMeasuredWidth` vs `getWidth` 的区别
- [ ] `invalidate()` vs `requestLayout()` 的区别（只重绘 vs 重新测量布局）
- [ ] **事件分发**（必考）：`dispatchTouchEvent` / `onInterceptTouchEvent` / `onTouchEvent` 三者的调用链、`ACTION_DOWN` 决定后续事件归属、`requestDisallowInterceptTouchEvent`、滑动冲突的两种解决范式（外部拦截 vs 内部拦截）
- [ ] 自定义 View 的三种方式、`onDraw` 里不能做什么、`Canvas` / `Paint` / `Path` 基础（你在 Compose 里画过，迁移概念很快）
- [ ] `RecyclerView`：四级缓存（`mAttachedScrap` / `mCachedViews` / `ViewCacheExtension` / `RecycledViewPool`）、`ViewHolder` 复用、`LayoutManager`、`ItemDecoration`、`DiffUtil` 与 `ListAdapter`
- [ ] `RemoteViews`、`SurfaceView` vs `TextureView`、`ViewStub`、`merge`、`include`
- [ ] **Compose 与 View 的对比**（你的优势题）：`RecyclerView` 复用 vs `LazyColumn` 的位置记忆、命令式 vs 声明式、`View` 的 `invalidate` vs Compose 的重组

### 3.4 Binder / IPC

- [ ] 为什么需要 Binder（对比共享内存 / Socket / 管道）、一次拷贝的原理（`mmap`）
- [ ] `AIDL` 的 `Stub` / `Proxy`、`oneway`、`Parcelable` 与 `Serializable` 的性能差异
- [ ] `Messenger` vs `AIDL`、`Binder` 线程池（默认 16 个）
- [ ] 跨进程的 `ContentProvider` 与 `Service` 实战（你的下载器 demo 可以加一个独立进程的 Service）

### 产出物

- [ ] 独立 demo 工程：**下载器**
  - 前台 Service + 通知（进度条）+ `CoroutineWorker` 下载
  - `Handler` 更新 UI、`IdleHandler` 做延迟初始化
  - 一个自定义 View（手写进度环，用 View 体系而非 Compose）
  - `RecyclerView` 列表（`DiffUtil`）
  - `BroadcastReceiver` 监听网络变化 → 暂停下载
  - （加分）独立进程 Service + AIDL 查进度
- [ ] 把 demo 推上 GitHub，README 写清覆盖了哪些知识点。**这个 demo 本身就是面试材料**：证明你不是只会 Compose。

---

## 8. 阶段 4：性能优化体系 + 网络（1 周）

### 4.1 性能优化（你需要「体系」而非零散技巧）

- [ ] **启动优化**：`Application.onCreate` 与 `Activity` 首帧的耗时来源、`ReportFragment` 埋点原理、启动窗口与 `windowBackground` 欺诈、`core-splashscreen` 的 `setKeepOnScreenCondition`（**你项目里就有**，可以讲）、异步初始化与任务依赖
- [ ] **卡顿**：16.6ms 预算、VSYNC、`Choreographer` 丢帧回调、`Systrace` / `Perfetto` 看帧、过度绘制、`BlockCanary` 原理
- [ ] **内存**：`LeakCanary` 原理（`ObjectWatcher` + `KeyedWeakReference` + hprof 分析）、常见泄漏场景（Handler / 单例 / 匿名内部类 / 资源未关）、`onTrimMemory`、`Bitmap` 内存计算与复用、`ShallowSize` vs `RetainedSize`
- [ ] **ANR**：四类触发条件（输入 5s / 广播 10s / Service 20s / ContentProvider）、`traces.txt` 怎么读、`StrictMode` 怎么用
- [ ] **包体积**：R8（混淆/压缩/优化）三件事的区别、`shrinkResources`、资源混淆、`APK Analyzer`、so 裁剪、`App Bundle`
- [ ] **电量与后台限制**：Doze / App Standby、后台启动限制、`WorkManager` 的约束与配额
- [ ] **Baseline Profile 与启动加速**（你项目的下一步动作）

### 4.2 网络

- [ ] HTTP/HTTPS 基础、TCP 三次握手四次挥手、`HTTPS` 握手与证书校验、`HTTP/1.1` vs `2`（多路复用、头部压缩、队头阻塞）
- [ ] **OkHttp 拦截器链**：`RetryAndFollowUpInterceptor` → `BridgeInterceptor` → `CacheInterceptor` → `ConnectInterceptor` → `CallServerInterceptor` 各自职责、应用拦截器 vs 网络拦截器的区别（**这是必考且你只有 1 个日志拦截器，必须补**）
- [ ] OkHttp 连接池与复用、`Dispatcher` 的并发模型（`maxRequests` / `maxRequestsPerHost`）
- [ ] 缓存机制：`Cache-Control` / `ETag` / `Last-Modified`、`OkHttp` 的 `Cache` 配置
- [ ] **SSE / 流式响应**：你的项目是明确记为「长期方案」的待办（`AIModule` 注释 + `OpenAiCompatibleModel` 的 KDoc「v1 统一按非流式处理」）。**实现真流式是一个绝佳的项目改造**，能同时讲清 OkHttp 流式读取、Flow 的 `callbackFlow`、背压、取消
- [ ] 超时三件套的实际含义（你设了 15/180/30s，能讲清为什么读超时给 180s 就是加分）
- [ ] 重试与退避策略、幂等性（你导入用唯一索引做幂等，可以串起来讲）

### 产出物

- [ ] **把 AI 调用改造成流式（SSE）**，用 `callbackFlow` 包 OkHttp 的流式响应。这一件事覆盖：OkHttp 原理 + Flow + 背压 + 取消 + 真实产品优化。
- [ ] 给 FitLog 补 Baseline Profile + Macrobenchmark，量化启动改善。
- [ ] （可选但强烈建议）接 LeakCanary 跑一遍，把发现写进 `docs/`。

---

## 9. 阶段 5：架构 + 测试 + 工程化（1 周）

### 要背的点

- [ ] MVVM / MVI / MVP 的边界，MVI 的「单向数据流 + 不可变状态 + Reducer」在 Compose 里为什么流行
- [ ] Clean Architecture 的分层与依赖方向、UseCase 的价值与滥用（**注意你项目没有 UseCase 层、没有模块化**，要能解释「为什么这个规模不需要」以及「多大才需要」）
- [ ] 模块化 / 组件化：按层分 vs 按功能分、`api` vs `implementation`、动态特性、路由方案的演进
- [ ] **Hilt 原理**：生成的 `Hilt_XXX` / `_HiltModules` / `DevLog`、`@Binds` vs `@Provides` 的选择、组件作用域与 `@InstallIn` 的对应关系、`@ViewModelScoped` / `@ActivityRetainedScoped` 的差异、`EntryPoint`（**你项目里用了，可以讲为什么**）
- [ ] `ViewModel` 为什么能在配置变更后存活（`ViewModelStore` + `NonConfigurationInstances`）、`onCleared` 时机、`SavedStateHandle` 与 `ViewModel` 的区别（**你项目里 `SavedStateHandle` 只用了 1 处，可以讲这处为什么需要**）
- [ ] `Lifecycle` 的实现（`LifecycleRegistry` + `ReportFragment` / `LifecycleOwner`）、`repeatOnLifecycle` 与 `flowWithLifecycle` 为什么比 `launchWhenStarted` 好
- [ ] `DataStore` vs `SharedPreferences`：为什么 SP 的 `apply` 会有 ANR 风险、DataStore 的 `updateData` 原子性与 `ReplaceFileCorruptionHandler`（**你项目里配了**）
- [ ] `WorkManager` 原理：`WorkSpec` 的持久化、约束如何被监听、`ExistingWorkPolicy` 各值的差异（**你项目里用了 `REPLACE` 和自链的 `APPEND_OR_REPLACE`，可以讲幂等守卫**）
- [ ] 依赖注入的替代方案（手写容器 / Koin）与取舍
- [ ] Compose 下的架构：状态提升、UI 事件单向传递、`Route` / `Screen` 分离（**你的项目就是这个范式，`XxxRoute` + `XxxScreen`**）

### 你的稀缺优势：测试

大部分候选人写不出测试，你有 59 个测试文件。要把这件事讲成能力而不是巧合：

- [ ] `@Volatile` / 测试替身：你用**手写 Fake**（`FakeAIApi`）而不是 MockK/Mockito。为什么？——「Fake 有真实行为、可断言交互序列；Mock 只验证调用发生过。测 Flow 时序时 Fake 更可靠」。这是高水平回答。
- [ ] `MainDispatcherRule` 与 `TestDispatcher`：`UnconfinedTestDispatcher` vs `StandardTestDispatcher` 的区别（前者立即执行、后者需 `advanceUntilIdle`）
- [ ] `runTest` 的虚拟时间：为什么 `delay(1000)` 不会真的等 1 秒
- [ ] Robolectric 在 JVM 跑 Room 测试的原理（**你项目里两套都跑了：JVM + Robolectric 内存库，以及设备上的 `AndroidJUnit4`**）
- [ ] Compose UI 测试：`createComposeRule`、语义树查找、`assertIsDisplayed`
- [ ] 补概念：**Turbine**（测 Flow 的标配，你项目没用）、**MockK** 的基本用法（面试会问）、测试金字塔、覆盖率的意义与陷阱

### 工程化（你的隐藏加分项）

- [ ] **R8**：`-keep` 规则设计、反射与序列化的 keep 需求、`-if/-keepclassmembers` 条件规则、consumer rules、`-dontwarn` 的作用。**你的 `proguard-rules.pro` 是活教材**：
  - kotlinx-serialization 的 `-if ... -keepclassmembers` 条件 keep 规则（比无脑 `-keep class ** { *; }` 精确得多）
  - ADK 传递依赖 kxml2 与 Android 平台 `XmlPullParser` 的重复类冲突 → 排除依赖 + `-dontwarn`。**「重复类的成因与处置」是一道能压住很多人的题**
- [ ] KSP vs KAPT：KSP 为什么快（不生成 Java 桩、不跑注解处理器两轮）、代价是什么（处理器要适配）
- [ ] CI：你已有 `testDebugUnitTest` + `lintDebug` + `assembleRelease`（含 R8 门禁）。讲清「为什么把 release 构建放进 CI」（R8 的 keep 规则错误只在 release 暴露）
- [ ] 版本目录（`libs.versions.toml`）+ BOM 的单一事实源管理（**你项目里写得很规范**，能讲依赖收敛）

---

## 10. 贯穿轨道

### 10.1 算法（每天 1 小时，不可挪用）

- 按主题刷，不要随机刷：数组/双指针 → 链表 → 哈希 → 栈队列 → 二叉树 → 二分 → 回溯 → DP → 堆/贪心 → 图
- 每主题 10~15 题，目标 150~200 题。**要能白板手写，不能靠补全。**
- 每题写 3 行笔记：思路关键词、易错点、复杂度。不写题解。
- 复习节奏：第 1 天 / 第 3 天 / 第 7 天 / 第 21 天。

### 10.2 JVM / Java 基础与并发

- [ ] **JMM**：主内存与工作内存、`happens-before` 八条规则、`volatile` 的两个语义（可见性 + 禁止重排序，**不含原子性**）。锚点：`AgentEngineImpl` 的 `@Volatile currentRunner`
- [ ] `synchronized` 原理：对象头 Mark Word、偏向锁/轻量级锁/重量级锁的升级（新 JDK 已废弃偏向锁，要知道）、可重入。锚点：`KeystoreManager` 的 `@Synchronized getOrCreateKey()`
- [ ] CAS 与 `AtomicXxx`、ABA 问题
- [ ] 线程池：七个参数、`ThreadPoolExecutor` 的执行流程、四种拒绝策略、为什么不建议 `Executors.newFixedThreadPool`
- [ ] `ThreadLocal` 原理与内存泄漏（`Entry` 的弱引用的 key + 强引用 value）
- [ ] GC：分代模型、可达性分析、GC Roots、主流收集器（CMS/G1/ZGC）的差异
- [ ] 类加载：双亲委派、打破委派的场景（SPI、热修复）
- [ ] 泛型擦除与它的后果、`Parcelable` 为什么比 `Serializable` 快

### 10.3 模拟面试（每周 1 次，30 分钟）

- [ ] 录音 / 录像，自己回看。**只在嘴上过一遍，和说出来，是两件事。**
- [ ] 结构：5 分钟项目串讲 → 3 个随机八股 → 1 条追问链往死里挖到讲不出
- [ ] 把「讲不出来」的位置记在一个 `断点清单` 里，下一周优先补
- [ ] 到第 4 周开始，把模拟对象换成真人（朋友 / 社区 / 付费模拟），外部反馈不可替代

---

## 11. 面试官从你项目里一定会追问的（追问链清单）

这份清单是本路线图最值钱的部分——从你的真实代码里长出来的追问。**每条都要能接住至少 2 层。**

### Room / 迁移

1. 「你手写了 Migration？那 Room 怎么判断该不该迁移？」
   → `RoomOpenHelper` 比对 `identityHash` / `schema` 版本 → 追问「`fallbackToDestructiveMigration` 什么时候触发」→ 追问「缺迁移时抛什么异常」（你已经知道：`IllegalStateException`，且是**有意**的失败显性化）
2. 「你为什么要在 6→7 里显式删除子表数据？不是有 `ON DELETE CASCADE` 吗？」
   → 迁移期间外键约束不生效（Room 在迁移时关闭了 FK / 未启用 `PRAGMA foreign_keys`），遗留孤儿子行会在迁移后的校验中让升级失败。**这题能答出来的人极少。**
3. 「`INSERT OR IGNORE` 和 `REPLACE` 有什么区别？你为什么用前者？」
   → `REPLACE` 是先删后插，会改变 rowid、触发级联删除。锚点：`ExerciseDao.upsertAllPreservingRows` 特意用 UPDATE-then-INSERT-IGNORE 以保住外键关联。
4. 「你的唯一索引是怎么保证导入幂等的？」
   → 从应用层 check-then-insert 下沉到 schema，消除 TOCTOU 竞态。
5. 「`@Relation` 的三级联查是怎么执行的？」
   → `@Transaction` + 多条查询内存拼接（不是 JOIN），追问「为什么必须 `@Transaction`」→ 追问「N+1 问题在这里存在吗」
6. 「`TypeConverter` 里为什么要容错？」
   → 锚点：`LocalDateConverters` 坏数据返回 EPOCH + 警告。追问「这样做的代价是什么」→ 答：坏日期会合并成同一天，是已知取舍（`docs/code-review-2026-09-02.md` 有记录）。**主动说出取舍 = 高级信号。**

### 协程 / Flow

7. 「你为什么用 `Mutex` 而不是 `synchronized`？」
   → 见 §1 的示例答案
8. 「`withContext(NonCancellable)` 解决什么问题？」
   → 会话结束时必须落库；追问「`NonCancellable` 内部的挂起点还能被取消吗」→ 不能，但要注意别在里面写成死循环
9. 「`runCatching` 有什么陷阱？」
   → 它会吞掉 `CancellationException`，破坏结构化并发。锚点：多处手动 rethrow。
10. 「`WhileSubscribed(5000)` 里为什么留 5 秒？」
    → 配置变更 / 短暂不可见时不必重启上游；追问「你哪里不能用它」→ `MainViewModel.appearance` 必须 `Eagerly`，因为 Splash 的 `setKeepOnScreenCondition` 只读 `.value`，用 WhileSubscribed 会导致永远没有订阅者、Splash 死锁。**这是你项目里最有故事的一处。**
11. 「`flatMapLatest` 和 `combine` 的区别？」
    → 前者取最新并取消旧的，后者各自独立取最新值组合。锚点：`activeProvider` 用 flatMapLatest 切 provider 时自动取消旧流；`TodayViewModel` 用多层 combine 聚合多数据源。

### Compose

12. 「你说动画不触发重组，怎么证明？」
    → `AnimatedChartState` 只允许在 draw 作用域读动画值（逐帧重绘 + 仅 invalidate 绘制层）。追问「为什么 draw 阶段读状态不触发重组」→ 三阶段模型 + 快照读写追踪。**能答到快照层就赢了大多数人。**
13. 「`derivedStateOf` 解决什么问题？什么时候不需要？」
    → 解决「读状态比写状态少」的场景；锚点：`CollapsingTitleScaffold` 的滚动折叠。不需要的场景：只是单纯转换值。
14. 「`LaunchedEffect` 的 key 变了会发生什么？」
    → 取消旧协程、重启。锚点：`LaunchedEffect(prefill)` 一次性消费。
15. 「`LazyColumn` 的 `key` 有什么用？不加会怎样？」
    → 位置记忆与状态保持、动画正确性。锚点：多处以实体 id 作 key。
16. 「`@Immutable` 你是为了什么加的？」
    → 让编译器做稳定性推断、允许跳过重组（`docs/code-review` 里记录过这项修复）。
17. 「`Application` 里 `CompositionLocal` 注入自定义色是怎么做的？」
    → `LocalFitLogColors`，追问「`CompositionLocal` 的默认值机制和为什么推荐用静态 `compositionLocalOf`」

### 网络 / AI 引擎

18. 「你的 OkHttp 有几个拦截器？为什么读超时 180 秒？」
    → 1 个日志拦截器（未用 BODY 于 release、redact 了 auth 头）；180s 是因为 Agent 多轮 + 工具调用可能超过 60s。追问「拦截器链的顺序」「应用拦截器和网络拦截器的区别」→ 需要额外补课。
19. 「你手写了 ADK 的 `Model` 实现？怎么保证协议兼容？」
    → `OpenAiAdapters` 做双向类型翻译。**追问杀手**：「`repairDanglingToolCalls` 是干什么的？」→ 某些 provider 对「assistant 发了 tool_call 但缺失对应 tool 结果」的会话直接 400 并毒化整个会话，所以需要为悬空调用合成占位结果。追问「`truncateHistory` 为什么按字符数而不是 token」→ 因为不引入 tokenizer 依赖，是精度与包体积的取舍。
20. 「你的 Agent 怎么处理用户确认？」
    → `requireConfirmation = true` 的三个写工具 → 引擎发确认请求 → UI 弹窗 → `respondToConfirmation` 回 `FunctionResponse`。追问「如果回传失败会怎样」→ `ChatViewModel` 会恢复待确认弹窗，避免悬空 tool_call。
21. 「你的配置变了怎么处理？」
    → `AgentEngineImpl` 用 `@Volatile currentRunner` + `rebuildLock`（Mutex），配置 key 变化时重建 runner。
22. 「为什么不用流式？」
    → 主动交底：v1 统一非流式，`OpenAiCompatibleModel` 已预留流式方向。**然后说你的改造计划**（阶段 4 的产出物）。

### 工程化

23. 「R8 的 duplicate class 你怎么解的？」
    → ADK 传递的 kxml2/xmlpull 与 Android 平台内置的 `XmlPullParser` 冲突 → 在 `build.gradle.kts` 里 `exclude` 依赖 + `-dontwarn`。追问「R8 和 ProGuard 区别」「`-keep` 和 `-keepclassmembers` 区别」「`-if` 条件规则怎么用」→ 你的序列化规则就是活例子。
24. 「为什么把 `assembleRelease` 放进 CI？」
    → R8 的 keep 规则问题只在 release 暴露。
25. 「测试为什么用手写 Fake 而不是 Mock？」
    → 见 §9。
26. 「`WorkManager` 的自链幂等守卫是怎么做的？」
    → 检查是否已存在 `ENQUEUED` 的后继，避免重复自链。
27. 「为什么没用 `@HiltWorker`？」
    → 项目未引入 `androidx.hilt-work`，改用 `@EntryPoint`。**这题要小心**：要能说出 `@HiltWorker` 需要的额外配置（`HiltWorkerFactory` + 在 `Configuration.Provider` 里注入 + 关闭默认初始化），表明你只是没引入而不是不会。

---

## 12. 时间紧的降级方案

只有 3 周：

| 优先级 | 内容 |
|---|---|
| P0 | 阶段 -1 压缩为 5 天：只做第二遍决策考古的 Top 6 + 第三遍录音，放弃动手验证 |
| P0 | 阶段 0（项目讲稿）+ §11 追问链清单吃透 |
| P0 | 阶段 3.2 + 3.3（Handler / 事件分发 / measure-layout-draw）—— 考频最高且你完全空白 |
| P1 | 阶段 1 的协程原理（CPS + 结构化并发 + Flow） |
| P1 | 阶段 2 的重组机制 + 三阶段（你项目最厚，最容易讲出彩） |
| P2 | Activity 生命周期 / 启动模式、四大组件剩余三个（概念层能答即可，不追源码） |
| P2 | 算法按高频主题（数组/链表/树/DP/二分）刷 100 题 |
| 放弃 | Binder 源码、Compose SlotTable 源码、Hilt 生成代码细节（能讲概念即可） |

**注意**：时间再紧也不能跳过阶段 -1 的考古——AI 写的项目跳过考古去裸面，追问链清单里的 27 条一条都接不住。

---

## 13. 每周检查表（打印出来贴墙）

- [ ] 本周新增几个「带锚点的八股答案」？（目标 ≥ 8）
- [ ] 本周是否手写过一次原理图（协程状态机 / 事件分发链 / Binder 模型）？
- [ ] 算法题是否每天不断？（目标 7 题/周）
- [ ] 是否录了一次模拟面试并回看？
- [ ] 断点清单是否在变短？
- [ ] 是否有一件「项目改造」在推进（流式改造 / Baseline Profile / demo 工程）？
- [ ] **本周是否有代码是关掉 AI 自己写的？**（阶段 -1 期间此项必须为是）
- [ ] 是否有任何一个主题，你现在能讲 3 分钟不卡壳？（目标：每周 +2 个）
