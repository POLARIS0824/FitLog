# FitLog 业务逻辑全链路文档

> 追踪从 UI 交互到数据存储的完整流程，覆盖所有核心用户操作路径。

## 1. 应用启动链路

```
App Launch
  → FitLogApplication (@HiltAndroidApp)
  → MainActivity.onCreate()
    → installSplashScreen()
    → MainViewModel 创建
      → init { seedOrchestrator.seedIfNeeded() }  // 异步，不阻塞 Splash
      → appearance: combine(themeMode, dynamicColor).stateIn(Eagerly)
      → appearanceLoaded → isReady = true
    → splashScreen.setKeepOnScreenCondition { !isReady.value }
    → setContent { FitLogTheme → NavDisplay(TodayKey) }
  → TodayViewModel 创建
    → seedGate = seedOrchestrator.completed.filter { it }  // 等种子完成
    → sharedMaterials = combine(5 数据流).combine(4 补充流)
    → uiState = combine(assembled, aiPhase, uiFlow).combine(seedGate)
    → 种子完成 → uiState 首发 → Today 页渲染
```

**关键设计**：
- Splash 只等外观偏好（DataStore 一读即放，~50ms）
- 种子耗时由 Today 加载条承接（首装可能数秒）
- `isReady` 必须 `Eagerly`：`setKeepOnScreenCondition` 只读 `.value`，不构成订阅

## 2. Today 主页数据装配

### 2.1 数据流拓扑

```
Room Flow (响应式)                    一次性冷 Flow
├── weekWorkouts (本周训练)           ├── profileFlow (用户资料)
├── todayWorkouts (今日训练)          └── catalogFlow (动作目录)
├── allWorkouts (全部训练)
├── activePlan (激活计划, DataStore→Room)
└── nextSession (flatMapLatest 派生)
         │
         ▼
    combine → TodaySnapshot (5 元)
         │
         ▼ combine
    TodayExtras (4 元: latestWorkout, prevWeek, profile, catalog)
         │
         ▼ combine
    TodayMaterials → assemble() → TodayUiState
         │                              │
         │                              ▼ combine
         │                         aiPhaseFlow (AI 增强)
         │                              │
         ▼ combine(seedGate)            ▼
    uiState: StateFlow<TodayUiState> ← merge
```

### 2.2 AI 增强链路（Coach Insight）

```
sharedMaterials
  → map { toCoachInsightContext() }
  → distinctUntilChangedBy { fingerprint() }   // 去重：同状态不重复请求
  → flatMapLatest { context ->
      if (!eligible || !aiAvailable) → AiPhase.Hidden
      else → AiPhase.Loading
        → coachInsightRepository.getAiInsight(context)
          → readCache(fingerprint)             // DataStore 缓存命中？
          → 未命中 → AI 请求 (jsonMode, maxTokens=300)
          → parseCoachInsight(reply)           // 容错 JSON 解析
          → writeCache(fingerprint, raw)
        → AiPhase.Ready(insight)
    }
  → mergeAiPhase(规则版, aiPhase) → 最终 CoachInsightState
```

**降级策略**：未配置 AI / 无网络 / 解析失败 → 静默保持规则版文案，不显示错误。

### 2.3 规则版 Coach Insight（CoachInsightBuilder）

```
输入：profile, weekCompleted, weekTarget, latestWorkout, nextSession, todayCompleted, hour
输出：CoachInsightState(observation, recommendation, action, actionLabel)

逻辑：
- 今日已完成 → REST + 恢复建议
- 有下一课次且未练 → START_WORKOUT + 课次提示
- 无计划无训练 → START_WORKOUT + 自由训练建议
- 时段感知：早晨/下午/晚间不同问候语
```

## 3. 训练日志导入链路

```
DataImportScreen
  → 用户选择文件夹 (SAF Intent)
  → DataImportViewModel.onFolderSelected(uri)
    → MarkdownFileScanner.scanFolder(contentResolver, treeUri)
      → DocumentsContract 遍历子文件
      → 过滤 .md 文件
      → parseDateFromFileName("2026-05-07.md") → LocalDate
      → readFileContent() → 原始文本
      → MarkdownParser.preprocess() → 清理后文本
    → ScanResult(successes, failures)
    → 对每个 success：
      → workoutRepository.existsBySourceFileName(fileName)  // 去重
      → 已存在 → 跳过
      → 不存在 → AI 解析 Markdown → Workout 模型
      → workoutRepository.insert(workout)  // 事务级联写入
    → 更新 UiState（导入结果统计）
```

**去重机制**：`sourceFileName` 字段唯一标识来源文件，重复导入自动跳过。

## 4. AI 对话链路

```
ChatScreen
  → 用户输入文本 → ChatViewModel.onInputChange(text)
  → 点击发送 → ChatViewModel.send()
    → 校验：非空 && 非发送中
    → 用户消息立即上屏（乐观更新）
    → viewModelScope.launch {
        apiMessages = [SYSTEM_PROMPT] + messagesBeforeSend
        aiChatRepository.chat(apiMessages)
          → 获取激活配置
          → 构建请求 (url + headers + body)
          → 网络请求
          → 提取 choices[0].message
        → onSuccess: 追加 AI 回复到 messages
        → onFailure: 设置 errorMessage
      }
  → 错误 Snackbar 展示 → dismissError()
```

**注意**：对话历史不持久化（进程内），ViewModel 销毁即丢失。

## 5. 训练计划管理链路

### 5.1 激活计划

```
TodayScreen 计划选择弹层
  → TodayViewModel.onPlanSelected(planId)
    → workoutPlanRepository.setActivePlanId(planId)
      → DataStore.edit { prefs[ACTIVE_PLAN_KEY] = planId }
    → activePlanId Flow 发射新值
    → activePlan = flatMapLatest { getPlanByIdWithDetailsFlow(id) }
    → nextSession = flatMapLatest { getNextIncompleteSession(plan.id) }
    → sharedMaterials 重新组合 → uiState 更新
```

### 5.2 训练完成联动

```
训练记录写入后（导入/手动）
  → workouts 表变化 → Room Flow 重新发射
  → weekWorkouts / todayWorkouts / allWorkouts 更新
  → TodaySnapshot 重组装
  → weekCompleted 变化 → 进度条更新
  → todayCompleted 变化 → Coach Insight 指纹变化 → 触发 AI 重新生成
  → 计划课次完成标记：
    → workoutPlanRepository.markSessionCompleted(sessionId, workoutId)
    → planned_sessions.completedWorkoutId 更新
    → nextSession Flow 发射下一条未完成课次
```

## 6. 统计页数据链路

```
StatsScreen
  → StatsViewModel
    → period (MutableStateFlow: WEEK/MONTH/YEAR)
    → periodWorkouts = period.flatMapLatest { p ->
        range = StatsChartDataBuilder.rangeOf(p, today)
        workoutRepository.getByDateRange(range).map { PeriodWorkouts(p, it) }
      }
    → yearWorkouts = getByDateRange(heatmapStart, today)   // 53 周窗口
    → weightMetrics = bodyMetricRepo.getByDateRange(90d)   // 90 天窗口
    → combine(periodWorkouts, yearWorkouts, weightMetrics) { ... }
      → StatsChartDataBuilder.build()    // 容量柱状图
      → StatsOverviewBuilder.build()     // 概览网格
      → StatsHeatmapBuilder.build()      // 贡献热力图
      → StatsWeightBuilder.build()       // 体重曲线
    → uiState: StateFlow<StatsUiState>
```

### 体重录入子链路

```
StatsScreen 体重弹层
  → onWeightSheetOpened() → 读取今日记录预填
  → onWeightInputChange(value) → 更新表单
  → onWeightSubmit()
    → 校验：数字 && 20~300 kg
    → bodyMetricRepository.upsert(BodyMetric(today, weight))
      → BodyMetricDao.upsert() (REPLACE on date PK)
    → savedTick++ → Screen 关闭弹层
    → weightMetrics Flow 重新发射 → 图表更新
```

## 7. AI 服务商配置链路

```
AISettingsScreen
  → onProviderSelected(type) → 回填已保存配置到表单
  → onApiKeyChange / onModelChange / onBaseUrlChange → 更新表单 StateFlow
  → onFetchModels(baseUrl, customEndpoint)
    → 构造临时 config → aiChatRepository.fetchModels()
    → 成功：更新模型列表 + 持久化 cachedModels
  → onTestConnection()
    → 构造临时 config → aiChatRepository.testConnection()
    → 更新 TestState
  → onSave(config)  [config 由 Screen 组装]
    → aiProviderConfigRepository.insert(config)
      → config.toEntity() → KeystoreManager.encrypt(apiKey) → Room
    → aiProviderConfigRepository.setActiveProviderId(config.id)
      → DataStore.edit { ACTIVE_PROVIDER_KEY = id }
    → 全局生效：所有收集 activeProvider 的 Flow 收到更新
```

## 8. 用户资料管理链路

```
ProfileScreen
  → ProfileViewModel
    → init: userProfileRepository.getFirst() → 预填表单
    → onNameChange / onAgeChange / ... → 更新表单
    → onSave()
      → 校验 → UserProfile 模型
      → userProfileRepository.save(profile)
        → UserProfileDao.insert(entity) (REPLACE)
      → 成功提示
  → Today 页 profileFlow 重新发射 → 问候语/Coach Insight 更新
```

## 9. 外观设置链路

```
AppearanceScreen
  → AppearanceViewModel
    → themeMode / dynamicColor (DataStore Flow)
    → onThemeModeSelected(mode) → userPreferencesRepository.setThemeMode(mode)
    → onDynamicColorToggle(enabled) → setDynamicColor(enabled)
  → MainViewModel.appearance 重新发射
  → MainActivity setContent 重组 → FitLogTheme 切换
  → 全局 UI 即时响应（无需重启）
```

## 10. 种子数据初始化链路

```
MainViewModel.init
  → seedOrchestrator.seedIfNeeded()  // 进程内幂等
    → Mutex.withLock {
        exerciseSeeder.seedIfNeeded()
          → exerciseDao.getCount() == 0 ?
          → 是：从 assets/exercises/ 读取 JSON → insertAll()
        workoutPlanSeeder.seedIfNeeded()
          → workoutPlanDao.getAllPlans().isEmpty() ?
          → 是：PresetPlans 定义 → savePlanWithSessions()
      }
    → finally { _completed.value = true }  // fail-open
  → TodayViewModel.seedGate 放行 → uiState 首发
```

## 11. 数据同步与一致性

| 场景 | 机制 |
|------|------|
| 训练写入 → Today 刷新 | Room Flow 自动重新发射 |
| 计划激活 → Today 课次更新 | DataStore → flatMapLatest → Room Flow |
| AI 配置切换 → 全局生效 | DataStore activeProviderId → activeProvider Flow |
| 体重录入 → 统计图表更新 | Room Flow (body_metrics) |
| 外观修改 → 主题切换 | DataStore → MainViewModel.appearance |
| 种子完成 → Today 首帧 | SeedOrchestrator.completed StateFlow |

## 12. 已知业务逻辑问题

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| 1 | Chat 对话历史无长度限制 | 超出模型 context window 时请求失败 | 添加滑动窗口或 token 计数截断 |
| 2 | Chat 历史不持久化 | 进程死亡后对话丢失 | 考虑 Room 存储或至少 SavedStateHandle |
| 3 | 训练完成未自动联动计划标记 | 需手动标记课次完成 | 导入/手动记录时自动匹配 nextSession |
| 4 | 跨零点 Today 不刷新 | 日期/本周进度不准 | 监听 `Intent.ACTION_DATE_CHANGED` 或 WorkManager 定时刷新 |
| 5 | 动作库更新不反映到 Today | 自定义动作新增后 catalog 不刷新 | WhileSubscribed(5000) 重启时会刷新，可接受 |
| 6 | SystemPrompt 过于简单 | Chat 页 AI 回复质量有限 | 注入用户资料/训练上下文到 system prompt |
| 7 | 导入去重仅按文件名 | 同文件名不同内容会误跳过 | 可加 content hash 辅助判断 |
