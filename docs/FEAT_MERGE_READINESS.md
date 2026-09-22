# Feat 合入 Dev 验收记录

更新日期：2026-09-22。结论：本轮已确认的导入 bug 已修复，分支冲突已解决；设备验收仍待完成，不能据此宣称所有合并门禁通过。

## 验证范围

- 源代码提交：`4281da8f8daefe2b08e1948ef5c13c2f0392f21b`（feat）。本记录后续提交仅改文档。
- 目标 dev：`62dc285edd1de25150fa94370660bc119d49e119`。
- dev 已同步进 feat；`git merge-base --is-ancestor dev HEAD` 成功。目标不再前进时可快进合并。
- 首轮检查运行于 feat 工作区；随后在独立、干净的 `4281da8` worktree 上完整复验，排除了用户原有 `DatabaseModule.kt` 改动。该文件未被本轮修改或提交。
- 干净验收目录：`C:\Users\POLARIS\AppData\Local\Temp\FitLog-verify-4281da8`。该目录保留用于设备验收，只有本机 `local.properties` 与构建产物为忽略文件。
- 按用户要求，本轮不评估 Room migration；此结论不等于确认旧版数据库升级安全。

## 本轮提交与修复

| 提交 | 改动 | 验证 |
| --- | --- | --- |
| `d71b9be` | 导出添加 `FitLog-Time-Format: 1`；旧日志的 HH:mm 标签继续走旧解析；识别出的导出元数据必须同时具备开始、结束字段，各出现一次；空白值不再等同于显式“空” | 先运行回归测试复现 3 个失败，再修复；原同日、跨午夜、跨年、时区及 AI 覆盖防御测试通过 |
| `8c82a58` | 真实 WorkManager 测试添加可控 Worker 和 TestDriver，验证运行中 KEEP 恢复、BLOCKED 后继幂等、父成功后子任务启动与成功 | AndroidTest APK 编译通过；尚未在设备执行 |
| `4281da8` | 同步最新 dev，解决 AISettingsScreen 冲突；保留 feat 的共享 scaffold 用法，同时采用 dev 的共享标题吸附修复 | 全量单测与构建检查；设备交互验收待执行 |

此前七阶段修复涉及聊天草稿及历史恢复、计划动作按持久化 ID 匹配、导入保存期间编辑冻结、预览与落库共用清洗、时间戳往返、提醒测试及计划周频次推导。本轮新增测试针对后续 review 发现的缺口；没有把所有旧问题重新标记为经过设备验证。

## 自动检查

执行命令（PowerShell）：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
```

| 检查 | 本轮结果 |
| --- | --- |
| 单元测试 | 538 个，0 失败、0 错误、0 跳过 |
| Lint | 干净快照：0 错误、64 警告、2 提示；首轮工作区为 66 警告，差异为依赖版本提示 |
| Release / R8 | 构建通过，生成 unsigned release APK |
| AndroidTest APK | 编译通过；不代表设备测试通过 |
| Git | 无未解决冲突；dev 为 feat 祖先 |

以下路径相对于干净验收目录。

测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
Lint 报告：`app/build/reports/lint-results-debug.html`。

两条 `TrustAllX509TrustManager` 警告定位于依赖 `com.google.http-client:google-http-client:2.1.0` 的 JAR，不能归为“仅测试或 Mock 使用”。本轮没有验证这些依赖方法在实际网络调用路径中是否可达，也不将该警告直接等同于已确认的运行时 TLS 漏洞。其余警告不以“没有错误”为由宣称全部无风险。

## 合并前剩余验收

1. 连接 API 26 与当前目标 API 的设备，在上述干净验收目录执行 `.\gradlew.bat :app:connectedDebugAndroidTest`，保留结果。当前 `adb devices -l` 无设备，配置 SDK 下也没有 emulator 程序，本轮未执行设备测试。
2. 在设备上检查 AI 设置页的短内容/可滚动内容、滚动吸附和键盘遮挡；验证导入保存期间输入禁用与取消、跨午夜导出再导入、提醒接力。现有提醒测试覆盖受控交错，不宣称穷尽任意并发顺序。
3. 合并前重新确认 dev SHA；若目标前进，先同步并复验受影响行为。用户原有 DatabaseModule 改动仍未提交，不包含在本轮提交中。

完成上述验收后再执行 feat 到 dev 的合并。本轮没有修改 dev 分支指针或推送远端。
