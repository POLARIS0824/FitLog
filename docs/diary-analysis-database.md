# 日记分析数据库（Commit 3）

`diary-analysis.db` 是独立的 Room v1 数据库。保存解析尝试、候选、当前确认及旧确认快照，不保存输入全文；证据摘录允许保存。`source-index.db` 的扫描、重建和当前资料库切换没有分析库删除权限。禁止破坏性迁移，v1 schema 在 `app/schemas` 中保留。

## 数据和入口

- `parse_run`：独立尝试 ID、完整 `DiaryParseKey`、状态、时间、失败代码、原始响应及候选 JSON。同一个键允许失败后重试，只有成功记录可复用。
- `confirmed_diary`：按 `(vaultId, relPath)` 唯一的当前确认，保存最终日期、解析内容版本、来源解析 ID、修订号和接受部分结果标记。
- `confirmed_session` / `confirmed_exercise` / `confirmed_set`：场次、动作、展开后的组。父子外键级联，父记录内顺序唯一；一篇日记允许多场次及零场次。
- `confirmation_snapshot`：旧确认的完整树、旧确认时间和归档时间；来源解析外键禁止删除。快照不依赖当前子记录。

业务使用 `DiaryAnalysisRepository.get(context)`；启动时 `initialize()` 修复上次进程遗留的 RUNNING，失败状态通过 `storageState` 暴露，原文访问保持独立。所有新尝试和确认必须先完成初始化。同一个应用实例中初始化只执行一次，不会把新运行中的尝试再次标为中断。

真实模型尚未接入。后续适配器通过内部 `repository.executor(recordingParser)` 安装唯一的应用级执行器。`executor.parse(input)` 返回 `StoredDiaryParse`，包括尝试 ID、原有类型化结果及是否复用；原始响应不进入 `DiaryAnalysis`。从文件构建输入使用 `DiaryParseInput.fromSnapshot(sourceKey, snapshot, extractorVersion)`，它恢复文件读取器已移除的 BOM 后再归一化一次。

原始响应按收到的文本精确保留，包括非法 JSON；网络失败或超时没有响应时为空。候选 JSON 保存完整类型化分析，使用独立的存储格式版本 v1；确认快照也有独立的 v1 版本。损坏或不支持的缓存返回存储异常，不悄悄构造空结果，也不自动再次调用模型。

## 执行与异常

单消费者按提交顺序处理不同文件；同键在途请求共享尝试，完整键命中成功缓存后不发请求。只有实际开始执行才插入 RUNNING，队列不持久化，不保证退出进程后继续执行。模型返回后，原始响应、候选和终态一起写入事务。

预期模型失败记录为 FAILED，不影响后续文件，也不会自动重试。显式 `cancel(key)` 取消共享请求并尽力记为 INTERRUPTED；单个等待者取消不会取消其他等待者共享的工作。取消异常及编程异常继续传播。终态更新仅允许改动该尝试 ID 的 RUNNING 行，不能覆盖其他尝试或已经完成的状态。

数据库写入失败及意外执行错误停止执行器，后续排队请求也收到异常，不继续花费模型调用。应用退出或显式关闭执行器会取消其在途任务；残留 RUNNING 下次启动恢复。模型已返回但落库前进程终止，仍可能再次收费，当前没有服务端幂等保证。

## 审阅与确认

`suggestDiaryDate(sourceKey, analysis)` 只产生建议：三种文件名格式严格校验真实日期，文件名优先，其次唯一模型日期；冲突、非法日期或缺失需要审阅。`DiaryConfirmation.fromCandidate` 创建可修改的类型化审阅树；提交 `confirm(request)` 必须带最终日期、尝试 ID 和 `expectedRevision`（首次为 0）。日期不补今天，同篇场次使用统一最终日期。

确认返回 `Confirmed`、`RevisionConflict` 或带原因的 `Invalid`；数据库失败抛出 `DiaryAnalysisStorageException`。事务验证成功解析及来源身份、当前修订号和最终数据，原候选包含 ERROR 时要求 `acceptedPartialResult=true`。允许缺失数值，不允许负重量、非有限重量、非正次数或伪造的候选引用。审阅者可删除、增添及重排场次、动作和组。

原有组的逐字段来源从候选重新推导，`userEdited` 通过最终值与候选值比较，忽略调用方提供的来源标记。保留 INHERITED 与 inferred 同时存在、继承组位置和原始组内编号；手工组没有 AI 来源。重量转换根据最终重量和单位重算，不把单边重量乘二，不补自重。证据坐标仅适用于旧归一化全文；用户新增或修改的摘录没有未经验证的坐标。

替换确认前先归档完整旧版本，再替换子树、保留日记主记录 ID 并递增修订号，全部在一个事务中完成。旧审阅页返回修订冲突，不能覆盖刚保存的修改。新解析本身绝不更新确认数据。当前与历史确认引用的尝试都保留，本次不实现清理策略。

原文变化仍可确认本次审阅：始终保留它对应的解析哈希，不用新文件哈希重标旧数据。`confirmationFreshness(confirmed, currentContentVersion)` 派生已确认、待更新、无法核验或未确认；不可读文件及不同哈希算法版本无法核验。必须传归一化内容版本，不能传 Source Index 字节指纹。零场次不表示休息日，接受部分结果也不表示原文没有其他训练。

## 验证与后续

JVM 测试覆盖候选往返、原始响应、完整键复用、串行队列、取消、启动恢复、写入失败、修订冲突、事务回滚、来源信息、日期和文件变化；Robolectric 磁盘重开测试验证确认树、原始响应和历史持久化，以及独立重建 Source Index 不影响分析库。

运行 `./gradlew.bat testDebugUnitTest assembleDebug` 和 `git diff --check`。沿用现有系统备份规则；未验证真实设备上的系统恢复，手动导出恢复后续实现。真实模型、审阅界面、Log 状态展示、标准动作与别名归并、跨版本回放均不在本次范围内。
