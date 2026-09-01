# 代码审查报告（2026-09-02 · review/code-audit 分支）

本报告汇总本轮对全仓库的系统性审查结论。审查由四个并行视角组成：数据层
（Room/DAO/仓库/DI/模型）、UI 与导航层（MainActivity/组件/主题/util）、
Feature 层（Today/Stats/Chat/Agent/AISettings/Reminder）、种子与导入链路
（Seeder/MarkdownParser/SAF/测试覆盖），并交叉验证了 `app/schemas/` 的迁移
正确性与全库死代码引用。

## 审查范围外的既有事实

- **工作区基线**：审查开始时存在一处未提交改动（ChatScreen 输入栏胶囊化
  改版，含三个预留占位按钮）与两张未被引用的图片删除。该改动随审查分支
  一起携带，处理决策见"待办移交"。
- **工作区文件损坏**：`data/remote/dto/MessageDto.kt` 在 8 月 30 日被一段
  无关的 JavaScript 内容覆盖（字节数与原文件恰好一致，git 状态缓存未察觉，
  本轮编译才暴露）。已从 HEAD 恢复。

## 已修复（本分支，按提交分批）

### fix: 数据正确性与崩溃缺陷（第一批）

| 严重度 | 位置 | 问题与修复 |
|---|---|---|
| P1 | WorkoutRepository + WorkoutViewModel | 保存训练与计划课次完成回写是两个事务，进程死亡落在中间会留下"训练已保存、计划进度未推进"的永久缺口（v9 迁移加 planSessionId 正是为消除它）。修复：`finishSession` 增加 planSessionId 参数，回写并入同一事务；另加启动期 `reconcileCompletedFromWorkouts()` 对账兜底历史损伤 |
| P1 | AgentEngineImpl | `ensureUsableCredentials()` 抛异常逃逸出裸 `launch` → 未读出 API Key（换机恢复场景）直接崩溃而非展示引导卡。修复：改为返回可诊断异常对象，调用方转 `Result.failure` |
| P1 | WorkoutViewModel.addExercise | "插动作 → 插占位组"两步写，中间崩溃留下无组动作行。修复：仓库新增 `addExerciseWithPlaceholderSet` 单事务 |
| P1 | WorkoutViewModel.activeSession | stateIn 链无 catch，Room 上游异常击穿共享协程闪退。修复：接入 guard 降级 |
| P1 | MockStatsHistorySeederTest | instrumented 测试以 `fallbackToDestructiveMigration` 打开**生产库**并灌入一年假数据，connectedAndroidTest 每次运行即毁真机数据。修复：独立测试库名 |
| P2 | RoomChatRepository.clearAll | 全库唯一未入事务的多表写（消息与步骤两表"同批"清空）。已包 withTransaction |
| P2 | DatabaseModule | DataStore 无损坏处理器，CorruptionException 会击穿所有偏好流。已加 `ReplaceFileCorruptionHandler` |
| P2 | KeystoreManager.decrypt | 截断密文抛无关的 BufferUnderflowException。已加最短长度前置校验 |
| P2 | TrainingLevelCalculator.calculate | 0 次组按 Epley 退化为重量本身计入 1RM，与同文件 `bestOneRMSet` 的口径承诺矛盾。已过滤 reps>0 并补测试 |
| P2 | TodayViewModel | 切换计划不清空手动打卡集合，动作 key 跨计划共享导致新计划被凭空推到 COMPLETED。已在 onPlanSelected 清空 |
| P2 | ReminderWorker 自链 | 自链用 REPLACE 语义会取消运行中的任务自身（当前因 showNotification 无挂起点而侥幸无害）。修复：新增 `scheduleSelfChainedNext`（APPEND_OR_REPLACE），外部重排仍走 REPLACE |
| P2 | DataImportViewModel | 扫描失败后上一文件夹的结果残留且导入按钮可点，用户会重放旧快照。失败路径已清空结果 |
| P2 | AISettingsViewModel | 表单不全时 fetch/test/save 三个入口静默 no-op。已补一次性错误反馈 |
| P2 | GeminiFlowingGradientBackground | 渐变色板取 `isSystemInDarkTheme()` 而非生效主题——"系统深色 + 应用强制浅色"时首页主卡色彩错配。改为按 surface 亮度推导 |
| P2 | AndroidManifest | 未开启预测式返回（代码已按其设计）。已加 `enableOnBackInvokedCallback` |

### refactor: guard 模式与指标口径收口（第二批）

- 新增 `util/FlowGuard.kt`：guard 模式全局共享实现（catch → 错误回调 →
  fallback）。此前 TodayViewModel/StatsViewModel 各持一份私有复制，且
  WorkoutViewModel.activeSession、AISettingsViewModel 组合链完全未防护。
- `util/VolumeAggregator` 新增单动作粒度出口（`workingVolumeOf(log)` /
  `workingSetCountOf(log)`，后者明确排除 reps≤0 占位组），并迁移四处手写
  口径：Prompt.summarizeWorkout（AI 提示词）、WeekProgressCalculator 两处
  （其中分布环此前把占位组计入，已对齐）、FitnessTools.toSummaryDto。
  `TrainingLevelCalculator.calculate` 同步对齐并补测试。

### fix: 解析、种子与 UI 健壮性（第三批）

- MarkdownParser：先去列表前缀再 trim（`-  两空格` 粘贴排版不再漏首空格）、
  支持 `* `/`+ ` 列表标记、裸 `✖`（无 VS16）归一为 x。
- ExerciseSeeder：种子投放对账（映射丢弃留痕，防"数据集新增未知字段 →
  条目永久缺席且版本号照常置位"）；同名消歧校验 `seenIds.add` 返回值；
  BodyPartMapper 未知值由静默归入 CHEST 改为返回 null 丢弃（与 MuscleMapper
  同策略），测试同步更新。
- FitnessTools.getImportedWorkoutContent：工具载荷 8k 字符封顶——原生
  Gemini 路径没有 OpenAiAdapters 的装配层截断，工具侧收口保证双路径同限。
- DataImportScreen：扫描结果封顶渲染 20 行（非懒加载 Column 的组合风暴），
  文案明确"扫描根目录、子文件夹不递归"。
- ReminderScreen/AISettingsScreen：时间选择与服务商弹层改 rememberSaveable。
- StackedSnackbarHost：移除时机跟随退场动画实际结束（MutableTransitionState
  判定），替代固定 300ms（低刚度 spring 300ms 远未收敛，收起动画中途消失）。
- MainActivity：Today/Stats 根页 VM 提升到 Activity 作用域（ChatRoute 先例），
  切 tab 不再销毁重建（DB 重查、入场动画重放、打卡勾选丢失）。`findActivity`
  收口至 `util/ContextExt.kt`。
- TodayViewModel：移除材料流内两处死 `.value` 读取；todayPlan 在材料流内
  组装一次，UI 组装与 AI 指纹共用（消除重复组装与漂移面）。
- MetricChartCard/ChartData 状态类补 `@Immutable`（图表网格跳过无效重组）。

## 确认为"非死代码"的重要发现

数据层大量仓库方法（计划 save/delete/getPlanById、动作库 CRUD、体重
deleteByDate、AI 配置 update/delete 等）在生产代码中零调用，但**全部被
单元测试引用**——它们是等待 UI 接线的能力面，不是可删除的死代码。这一
结论直接支撑了 PM 分析（见 `docs/business-loop-roadmap-2026-09-02.md`）：
"调整计划/管理计划"的业务环断点不在数据层，而在 UI 层缺失。

## 未修复（记录在案）

| 优先级 | 问题 | 不修理由 |
|---|---|---|
| P2 | 导入幂等以裸文件名为唯一键：同名文件无法重导、跨文件夹同名冲突 | 唯一索引在 schema v7 已定，改键需要迁移；当前单用户场景可接受 |
| P2 | MarkdownFileScanner 不递归子文件夹 | SAF 递归枚举工作量与收益不成比例；已用文案明确约束 |
| P2 | SAF 目录授权不持久化（每次会话重选文件夹） | 无跨会话重扫需求前是 UX 取舍而非缺陷 |
| P2 | LocalDateConverters 损坏日期降级到 EPOCH（多行损坏数据合并到同一天） | 触发概率极低；改逐行跳过会动摇"流不可死"的降级语义 |
| P2 | CollapsingTitleScaffold 逐帧重组 / AISettingsScreen 内联复制共享组件 | 性能异味与复制异味成立，但重组范围有界；AISettings 改造回归风险大于收益，留作技术债 |
| P2 | 原生 Gemini 路径无历史截断/悬空 tool_call 自愈（仅工具载荷上限已补） | 完整对齐需动 ADK 会话层，超出本轮范围 |

## 待办移交（进入 feat 分支处理）

1. ChatScreen 输入栏的三个预留占位按钮（附加/语音/Live）当前是死控件——
   语音输入应在功能分支实现为真实能力（见路线图 F-06），Live/附加在能力
   就绪前应隐藏。
2. `assets/exercises/` 12MB、1322 张动作图片全库无消费者（imageUrl 列
   同样无 UI 读取）；其中 2 张（3215/3302）已先行删除。路线图 F-04 决定
   接线展示，接线时需对缺失资源优雅降级。
3. TodayScreen 四张"即将上线"占位卡（补剂摄入/肌群平衡/AI 分析/运动目标）
   是显式产品欠账，处置见路线图。
