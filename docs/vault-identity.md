# 资料库 UUID 与 SAF 地址

资料库身份 `vaultId` 使用随机 UUID，SAF 目录 URI 是访问地址。两者分别保存；索引查询、扫描状态、修复标记、日记设置和解析来源键使用 UUID。目录枚举、原文读写、系统授权和恢复位置继续使用 URI。

## 身份生命周期

- `VaultPreferences` 在 `vault_preferences` DataStore 中保存当前 `vault_uri`、`vault_id` 和历史 URI → UUID 映射。
- 首次确认连接时生成 UUID，并与 URI 在同一事务中保存。选择但未确认的目录不登记身份。
- 旧配置读取时，在持久化 UUID 后才发布 `Configured(uri, vaultId)`。并发读取和重复连接共用同一 UUID；迁移写入失败明确报告配置失败，不发布临时身份。
- 切换到不同 URI 使用独立 UUID；返回已知 URI 复用 UUID。解除连接仅清除当前 URI 和 UUID，保留历史映射、日记设置、索引、草稿及系统授权。
- 恢复历史资料库记录时可以登记其原 URI 的 UUID，不改变当前连接。
- 暂不自动识别不同 URI 是否对应同一物理目录；不同 URI 分配不同身份，重新关联需另行实现。

## 旧数据兼容

- 旧 URI 配置保留原键名。原日记设置复制到 UUID 键；若 UUID 设置已存在，以它为准。
- Room 在首次访问某个 UUID 时，事务迁移该 URI 下的来源和扫描记录；已存在的 UUID 来源优先。迁移可重复执行，失败保留旧记录，后续访问可重试。
- Room 的 SQL 列名 `vault` 和数据库版本 2 保持兼容，Kotlin 属性为 `vaultId`。本次迁移改变键值，不改变 SQL 表结构。
- 索引的旧 URI 修复标记先登记到 UUID 下，再清除旧标记；Markdown 保存后的索引准备失败也留下修复标记。
- 草稿与备份增加可选 `vaultId` 字段；旧文件名和 `vault` URI 定位字段保留，避免改变恢复记录身份及原位置。恢复旧记录时补齐 UUID；UUID 与 URI 的已有映射不符时阻止继续编辑，仍可查看或导出。
- 旧导航记录允许缺少 UUID；索引更新和设置读写使用原 URI 补齐身份，不套用当前资料库。

## 验证

运行 JVM 测试与 Debug 构建：`./gradlew.bat testDebugUnitTest assembleDebug --offline --console=plain`。

新增测试覆盖旧配置迁移、并发身份创建、切换与解除后重连、设置保留、损坏身份与写入失败、Room 迁移去重与重开、UUID 索引与 URI 访问分离、修复标记迁移以及历史恢复身份核对。

本轮验证通过：26 个测试套件、196 个 JVM 测试，失败、错误、跳过均为 0；Debug 构建和 `git diff --check` 通过。未运行 AndroidTest 或真机验收。

真机升级后应确认旧资料库、日记设置与恢复记录仍可访问；解除并重新连接同一目录后，索引和设置仍属于同一资料库。JVM 验证不替代真实 SAF 提供方验收。
