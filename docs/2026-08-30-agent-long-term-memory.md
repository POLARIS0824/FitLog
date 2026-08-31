# AI 教练长期记忆机制（ADK Memory）

> 状态：已实现（分支 `feature/adk-memory`）｜实现日期：2026-08-30｜基于 ADK Kotlin 0.8.0

## TL;DR

AI 教练现在拥有**跨会话的长期记忆**：清空对话时，整段会话被归档进设备本地的 AppSearch 记忆库；之后的每次对话，ADK 的 `PreloadMemoryTool` 会用当前输入自动检索记忆库，把相关的历史片段以 `<PAST_CONVERSATIONS>` 块注入系统指令——于是"清空对话"不再等于"教练彻底失忆"，用户提过的偏好、伤病、约定能在新会话中自然延续。存储用 ADK 0.8.0 Android 版内置的 `AppSearchMemoryService`（零自写存储代码，数据不出设备），检索注入落点在系统指令内部，OpenAI 协议翻译层零改动。

## 一、四层记忆全景：长期记忆解决什么问题

FitLog 的教练 Agent 有四类"记性"，职责边界清晰，长期记忆补的只是其中缺了一层：

| 层 | 载体 | 内容 | 生命周期 | 谁消费 |
|---|---|---|---|---|
| 会话上下文 | `adk_sessions.db`（ADK `RoomSessionService`） | 完整事件流：消息、工具调用/响应、确认对 | 单会话，`clearSession` 删除 | 每轮请求的对话历史（ADK 回放进 `contents`） |
| 聊天记录 | `fitlog.db` 的 `chat_messages` + `agent_steps` | UI 消息与过程时间线 | 单会话，清空时一并删除 | 界面回放，与模型无关 |
| **长期记忆** | **AppSearch 本地库 `adk_memory`（ADK `MemoryService`）** | **跨会话的对话要点：偏好、伤病、约定、教练结论** | **跨会话，只有卸载/清应用数据才消失** | **`PreloadMemoryTool` 每轮检索注入** |
| 结构化数据 | `fitlog.db` 业务表 | 训练记录、计划、体重、动作库 | 随业务数据 | `FitnessTools` 工具实时查询，**永远不需要 memory** |

设计立场：结构化数据走工具查询，模型永远拿到真实数据；长期记忆只承载**对话中产生、无法从业务表查出的非结构化知识**（"用户不喜欢跑步""上周约好这周减量"）。两者互补不重叠。

## 二、ADK 的 MemoryService 抽象

### 2.1 接口

```
com.google.adk.kt.memory.MemoryService（0.8.0，全部为 suspend）
├─ addSessionToMemory(session)            // 抽象：整段会话归档
├─ searchMemory(appName, userId, query)   // 抽象：按查询检索 → SearchMemoryResponse
├─ addEventsToMemory(...)                 // 可选（默认抛 UnsupportedOperationException）
└─ addMemory(appName, userId, memories)   // 可选（默认抛 UnsupportedOperationException）
```

作用域是 `(appName, userId)` 二元组，没有按会话的分区概念——记忆天然跨会话。

### 2.2 内置实现与选型

| 实现 | 存储 | 适用性 |
|---|---|---|
| `InMemoryMemoryService` | 进程内存（默认挂载） | 不持久，进程死即失忆；**检索正则 `[A-Za-z]+` 完全无视中文** |
| **`AppSearchMemoryService`（本项目选用）** | **AndroidX AppSearch 本地存储** | **持久、数据不出设备、依赖已随 ADK POM（runtime scope）打包，零自写存储代码** |
| `VertexAiMemoryBankService` / `VertexAiRagMemoryService` | Google Cloud | 需 GCP 项目与服务账号，与应用 BYO-API-Key 直连架构冲突，不适用 |

代价与对策：AppSearch 层在本机（无设备）不可测试，中文分词召回质量需真机验证。逃生舱：引擎只依赖 `MemoryService` 接口，日后可平滑换成自写 Room 实现（中文子串匹配 + 无命中回退最近条目），调用方零改动。

## 三、代码地图

| 文件 | 职责 |
|---|---|
| `di/AgentEngineModule.kt` | `@Provides @Singleton MemoryService = AppSearchMemoryService.fromContext(context)`；进程级单例，跨 runner 重建共享 |
| `feature/agent/engine/AgentEngineImpl.kt` | 注入 `MemoryService` → 传入 `InMemoryRunner(memoryService=...)`；`clearSession` 归档后删除；`buildCoachInstruction` 追加记忆块使用规则 |
| `feature/agent/engine/AgentEngine.kt` | `clearSession` 契约 KDoc（先归档后删除、失败不阻断） |
| `feature/chat/ChatViewModel.kt` | `onClearChat` → `agentEngine.clearSession`（归档在引擎内部完成，VM 无感） |
| `feature/chat/ChatScreen.kt` | 清空二次确认弹窗文案（注明历史要点将归档为长期记忆） |
| `feature/agent/engine/OpenAiAdaptersTest.kt` | 锁定：system 指令多 text part 合并为单条 system 消息（记忆注入路径的回归防线） |
| ADK 侧（外部） | `com.google.adk.kt.memory.appsearch.AppSearchMemoryService`（存储）、`com.google.adk.kt.tools.PreloadMemoryTool`（检索注入） |

## 四、写入路径：会话归档

### 4.1 触发点与流程

唯一的写入触发点是 `clearSession`（用户点"清空对话"，或协议坏历史的自愈清空）：

```
用户点击清空（ChatScreen 二次确认）
  └─ ChatViewModel.onClearChat()            // UI 先乐观重置，失败回滚（既有逻辑）
       └─ AgentEngineImpl.clearSession(sessionId)
            ├─ 1. sessionService.getSession(key)                    // 读完整会话
            ├─ 2. events 非空 → memoryService.addSessionToMemory(s) // 归档进 AppSearch
            │      └─ 失败 → Log.w("AgentEngine", "会话归档进长期记忆失败")，继续
            ├─ 3. sessionService.deleteSession(key)                 // 删除会话历史
            └─ 整体包在 runCatching + CancellationException 透传包装内
                 └─ ChatViewModel onSuccess → 清 chat_messages / agent_steps
```

关键语义：

- **归档失败不阻断删除**。清空的主语义是自愈坏历史（悬空 tool_call 等），不能因记忆库故障而失败。AppSearch 会话是惰性打开的，首次归档的初始化异常也在 `runCatching` 内兜住。
- **删除失败则整体失败**，UI 回滚（既有行为），此时会话已被归档过一次——归档是幂等 upsert（见 4.3），后续再次清空不会产生重复记忆。
- **常规聊天不归档**。会话删除是明确的"这段对话结束了"信号；v1 不做聊天中途的增量归档（见 10. 演进路线）。

### 4.2 什么会成为记忆

AppSearch 实现的过滤规则（ADK 侧 `eventToRecord`）：**任何 content 含非空 text part 的事件都成为一条记忆文档**。具体地：

| 事件类型 | 是否归档 |
|---|---|
| 用户消息文本 | ✅ |
| 模型最终回复文本 | ✅ |
| 模型中间轮"思考"文本（非 final 的说明文本） | ✅（与 UI 回放过滤不同，这里更宽） |
| 纯工具调用 / 工具响应事件（无 text part） | ❌ |
| `adk_request_confirmation` 合成调用/响应对 | ❌（无 text part） |
| 错误事件、partial 流式片段 | ❌（无 text / 由 ADK 会话层排除） |

每个事件一条文档，`id = event.id`，并带 `author`（`user` / `fitness_coach`）与 ISO-8601 `timestamp`。**`sessionId` 刻意不存**——记忆按设计跨会话，检索时无法区分"来自哪次对话"。

### 4.3 AppSearch 存储细节

- **库名**：`adk_memory`（AppSearch LocalStorage，应用私有目录）。
- **Schema**：类型 `"Memory"`，版本 1，6 个属性——只有 `text` 建全文索引（`INDEXING_TYPE_PREFIXES` + `TOKENIZER_TYPE_PLAIN`），`author` / `timestamp` / `entryId` / `contentJson` / `customMetadataJson` 均不索引。首次使用时 `setSchema(forceOverride=true)`；官方注释提醒：未来不兼容的 schema 变更应升版本 + 配 Migrator，而不是依赖 forceOverride 的清库行为。
- **命名空间**：`appName + "\u001F" + userId`，本项目恒为 `fitlog␟local_user`（单用户单命名空间）。检索按 namespace + schema 过滤。
- **写入形态**：`addSessionToMemory` 内部等价于按事件批量 `put`（同 id 同 namespace 的文档会被 upsert 覆盖）。
- **会话生命周期**：AppSearch 会话惰性打开（mutex 串行化首次创建后缓存复用）；`MemoryService` 是进程级 Hilt 单例，随进程存活不显式 `close()`——与 `RoomSessionService` 同模式，切换 AI 服务商触发 runner 重建时记忆服务不动。

## 五、读取路径：检索与注入

### 5.1 时序

```
用户发消息（或确认续传）
  └─ runner.runAsync(...)
       └─ 每次调用模型前，LlmAgentTurn.prepareRequest 逐个执行工具的 processLlmRequest
            └─ PreloadMemoryTool:
                 ├─ query = invocationContext.userContent 的全部非空 text part（空格连接）
                 │    ├─ 无文本（如确认续传的 FunctionResponse）→ 本次请求不注入，返回
                 ├─ searchMemory("fitlog", "local_user", query)
                 │    ├─ AppSearch: namespace 过滤 + 前缀词项匹配 + 相关度排序
                 │    └─ 返回首页 ≤25 条（MAX_RESULTS=25，v1 只取第一页）
                 └─ 命中非空 → 记忆块经 LlmRequest.appendInstructions 追加到
                      config.systemInstruction（与教练指令以空行连接）
```

要点：

- **每轮模型调用都会检索一次**。一次运行内多次模型调用（工具循环）时，每次请求都重新注入；query 恒为该次运行的用户原始消息，故结果一致。
- **PreloadMemoryTool 的 `declaration()` 为 null**——模型永远看不到它、不能调用它，因此**不消耗 `maxSteps=8` 的工具步数预算**，也不产生额外模型往返（区别于 `LoadMemoryTool`：那个是模型自主调用的检索工具，要花一步）。
- **无命中即无注入**：查询无结果时请求原样发出，不产生空记忆块。
- **记忆服务未挂载时静默跳过**：`PreloadMemoryTool` 从 `invocationContext.memoryService` 取服务（runner 构造时传入，本项目恒非空）。

### 5.2 注入内容的形态

```
The following content is from your previous conversations with the user.
It may be useful for answering the user's current query.
<PAST_CONVERSATIONS>
Time: 2026-08-29T20:15:30Z
user: 我左膝有旧伤，深蹲重量得控制
Time: 2026-08-29T20:15:48Z
fitness_coach: 建议先从空杆开始重建动作模式，本周不做大重量下肢日
</PAST_CONVERSATIONS>
```

每条记忆一行 `Time:`（有则显示）+ `author: text`。最终请求里，教练动态指令（人设、规则、当日上下文快照）在前，记忆块紧随其后（ADK `appendInstructions` 以空行连接）。

### 5.3 到达模型的最终形态（协议路径）

- **OpenAI 兼容服务商**（OpenAI/DeepSeek/Moonshot/硅基流动/Azure/自定义）：`OpenAiCompatibleModel` 读取 `request.config.systemInstruction`，`OpenAiAdapters.toOpenAiMessages` 把指令 Content 的**全部 text part 拼接为单条 `role="system"` 消息**置于首位。记忆块作为额外 text part 天然走这条已验证路径——翻译层的三条分派规则（函数响应按 part 类型分发、确认对剥离、悬空调用降级）完全不受影响。已补测试锁定此行为。
- **Gemini 原生端点**：ADK 内置 `Gemini` 模型原生处理 `systemInstruction`，同样安全。

这也是当初风险评审的结论：记忆注入**只走系统指令通道**，绝不以 user/model 消息或函数部件形态进入历史——后者会被适配层的 part-type 分派误处理（例如与 FunctionResponse 混排的 text 会被丢弃）。

### 5.4 教练指令的记忆使用规则

`buildCoachInstruction` 追加一行，防止模型把记忆块当"当前上下文"复述或暴露技术来源：

> 指令附带 `<PAST_CONVERSATIONS>` 记忆块时，将其视为与该用户过往对话的存档：自然参考其中的偏好与约定作答，不要提及记忆或存档等技术来源

## 六、一次对话的完整示例

1. 用户：*「我左膝有旧伤，深蹲重量得控制」* → 教练给出建议。
2. 用户清空对话 → `clearSession`：该会话含文本的事件（上面两条 + 后续往返）各成一条文档写入 AppSearch；`adk_sessions.db` 中该会话删除；本地 `chat_messages`/`agent_steps` 清空。
3. 用户发起新会话：*「今天练什么」* → `PreloadMemoryTool` 以「今天练什么」为 query 检索，命中含"左膝""深蹲"的记忆（AppSearch 前缀词项匹配）→ 记忆块注入 system 消息 → 教练回答时自然规避大重量深蹲，且不会说"根据我的记忆"。

## 七、隐私与持久化

- AppSearch LocalStorage 完全在**应用私有存储**内，数据不出设备、无网络传输；服务商收到的只是每轮注入的记忆文本（与聊天内容本就相同的通道）。
- **不在 SAF 备份范围内**：现有 SAF 备份导出的是 `fitlog.db` 等业务数据，`adk_memory` 与 `adk_sessions.db` 一样暂不在备份之列——恢复备份/换机会失忆（已知限制，见 10）。
- 卸载应用或清除应用数据即彻底消失。

## 八、中文检索质量：机制与验证

检索链路的三个事实决定了中文效果：

1. **索引侧**：`text` 属性用 `TOKENIZER_TYPE_PLAIN` + `INDEXING_TYPE_PREFIXES`——AppSearch 按其内部分词（ICU 词边界，CJK 行为依实现）切成词项并索引前缀。
2. **查询侧**：查询原文直传（无改写/转义），`TERM_MATCH_PREFIX`——查询词项按前缀匹配索引词项。
3. **排序**：`RANKING_STRATEGY_RELEVANCE_SCORE` 相关度排序，首页 ≤25 条。

对照：ADK 自带的 `InMemoryMemoryService` 检索正则是 `[A-Za-z]+`，**中文 query 永远匹配不到任何记忆**——这是当时放弃默认实现、也放弃"直接用它"的关键原因。AppSearch 的 CJK 分词在真机上通常可用但无法在本机验证，因此：

- **真机验证方法**：归档一段含明确关键词的对话 → 新会话用同义词提问 → 观察是否命中；logcat 无 AppSearch 异常。
- **逃生舱**：`MemoryService` 接口不变，实现换成自写 Room（中文子串匹配 + 无命中回退最近 N 条），引擎与工具零改动。

## 九、失败语义一览

| 故障 | 行为 | 用户可感知 |
|---|---|---|
| 归档前读会话失败 | Log.w（tag `AgentEngine`）"归档前读取会话失败，跳过记忆归档"，继续删除 | 无 |
| `addSessionToMemory` 失败（含 AppSearch 惰性初始化异常） | Log.w "会话归档进长期记忆失败"，继续删除 | 无（仅丢这一次记忆） |
| 删除会话失败 | `clearSession` 整体失败，UI 回滚（既有逻辑） | "清空会话失败，请重试" |
| 检索时记忆库异常 | 该请求不注入记忆 | 教练本轮"忘性大"，无报错 |
| 查询无命中 | 不注入 | 无 |
| 未配置服务商 | `clearSession` 不依赖服务商配置，清空仍会正常归档并删除 | 无（发送侧另有引导卡） |

## 十、已知限制与演进路线

| 限制 | 说明 | 可能的演进 |
|---|---|---|
| 仅清空时归档 | 常规聊天不写记忆；用户长期不清空则记忆库里没有新内容 | 定期/会话增量 `addEventsToMemory` |
| 无蒸馏 | 原文归档，闲聊也会成为记忆，检索噪声随体量上升 | 归档前 LLM 蒸馏成要点 |
| 无上限增长 | 跨会话累积，只进不出 | 容量上限 + 淘汰；去重合并 |
| 无管理界面 | 用户看不到、改不了记忆内容 | 设置页"教练记住了什么"列表 + 删除 |
| 不在 SAF 备份内 | 换机/恢复备份后记忆清零 | 备份格式纳入 `adk_memory` 导出 |
| 检索质量依赖 AppSearch 分词 | 中文召回待真机验证 | 自写 RoomMemoryService（接口不变） |
| 单用户单命名空间 | `local_user` 硬编码，与现状一致 | 多账户时按 userId 自然隔离 |

## 十一、测试防线

- `OpenAiAdaptersTest.system instruction with memory block merges into single system message`：模拟 `PreloadMemoryTool` 追加记忆块后的多 text part system 指令，断言合并为单条 system 消息且两块文本都保留——注入路径的协议回归锁。
- `ChatViewModelTest`：391 项全绿基线（含修复的构造器漂移与清空断言）。
- `assembleRelease`：R8 + 资源收缩通过——AppSearch 路径首次被实际引用，混淆无裁坏。
- 真机冒烟清单（本机无设备，交付时待验证）：① 有约定的对话 → 清空 → 新会话追问记忆；② 聊天中提及记忆内偏好，观察主动结合；③ logcat 查 AppSearch 异常与中文召回。

## 十二、排障速查

- **教练"完全不记得"**：先确认此前是否真的清空过对话（唯一归档触发点）；再查 logcat `AgentEngine` 是否有归档失败告警；最后用 5.1 的命中条件自查 query 是否真的与记忆文本有词项重叠。
- **记忆内容"太啰嗦"**：中间思考文本也会归档（4.2），属预期；体量问题走"蒸馏/上限"演进项。
- **改了 API Key / 换服务商后记忆还在吗**：在。`MemoryService` 是进程级单例，与 runner 重建（配置键变化）解耦。
- **想彻底重置记忆**：目前无 UI 入口——清除应用数据或卸载重装（连带 `adk_sessions.db` 一起重置）。
