# 日记分析数据库

`diary-analysis.db` 是独立的 Room schema 2 数据库，保存解析终态、AI 候选和当前用户确认。Source Index 重建与资料库切换不删除分析数据。Markdown 原文不写入分析库；原始模型回答和证据摘录可以保存。

## 直接解析与读取

内部入口为 `repository.parse(input, recordingParser)`。调用方持有协程并防止重复按钮动作，每次显式调用只请求模型一次；没有队列、共享等待者、成功缓存复用、运行中持久化状态或启动恢复。

请求前先打开数据库；失败时不调用模型。预期模型失败保存为 FAILED，成功保存为 SUCCEEDED；一条插入同时写入时间、来源及内容版本、失败代码、精确原始回答和候选 JSON。取消及编程错误传播；取消或进程终止可能没有此次尝试记录。模型已调用但落库失败时明确报错，只能显式重试。

批量调用以后可在调用方顺序循环。每次传入自己的 parser；仓库不固定第一份模型配置。当前尚未接入配置页及手动解析按钮。

`DiaryAnalysisReader` 按 SourceKey 观察尝试和当前确认，打开详情不会调用模型。最近失败不遮住旧成功候选；损坏候选明确提示，仍可独立读取确认。候选 JSON 当前为存储格式 2，损坏或版本不符明确失败，不兼容读取或自动重新解析。

## 当前确认

- parse_run 保存终态及完整 DiaryParseKey；同一内容可以有多个显式尝试。
- confirmed_diary 按 UUID/相对路径唯一，保存最终日期、解析内容版本、来源尝试和确认时间。
- confirmed_session、confirmed_exercise、confirmed_set 保存当前确认树，使用父子外键与顺序约束。
- 确认组保存普通重量、单位、口径、次数、重量换算和组级 userEdited。逐字段推断及继承仍在 AI 候选中。

`DiaryConfirmation.fromCandidate` 创建审阅树。提交必须明确携带日期与成功尝试，不能补今天。仓库验证来源匹配、引用有效且不重复、有限非负重量、正次数和日期范围；允许缺失值。含语义 ERROR 的候选需要 `acceptedPartialResult=true`。

userEdited 由四个最终值与原候选比较得到，手工组为 true；不相信调用方传入标记。重量换算根据最终值重算，单边重量不乘二。确认返回 Confirmed 或 Invalid，数据库失败抛出 DiaryAnalysisStorageException。

显式确认在一个事务中替换当前树并保留主记录 ID；失败全部回滚。没有历史快照及 revision。重新解析本身只添加候选，绝不更新确认。原文变化通过归一化内容版本派生待更新或无法核验；不把旧结果改标成新文件版本。零场次不表示休息日。

证据保存 segmentId 和 quote，不保存偏移；候选摘录匹配仍在本地校验。详情将摘录作为旧版本证据展示。

## 本次开发安装

作者已确认没有需要保留的用户修正。本次允许单独重建旧分析库；源码没有迁移、兼容读取或自动破坏性迁移。旧 schema 1 安装直接打开分析库会明确失败，原文访问仍独立。

在可使用 run-as 的 Debug 安装上，升级前执行以下命令关闭应用并只清理分析库，然后安装本次 Debug APK：

```powershell
adb shell am force-stop com.example.fitlog
adb shell run-as com.example.fitlog rm -f databases/diary-analysis.db databases/diary-analysis.db-wal databases/diary-analysis.db-shm databases/diary-analysis.db-journal
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

这些步骤保留 Markdown、editor-drafts、DataStore 中的 UUID 映射及设置。不要通过卸载或“清除 App 数据”替代。以后已有确认修正时，格式变更必须先成功导出 JSON 快照，再单独重建。

## 验证

2026-10-06：250 项 JVM 回归通过，Debug 构建和 AndroidTest 源码编译成功，当前 schema 2 已导出。测试覆盖每次显式请求、原始回答、整体解码失败、取消、落库失败、旧 schema 拒绝且不花费模型调用、候选读取、组级编辑标记、确认事务回滚，以及磁盘重开和 Source Index 重建后确认保留。

真实模型质量、SAF/UI 与系统恢复仍需设备验收。
