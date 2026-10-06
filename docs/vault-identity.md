# 资料库 UUID 与 SAF 定位器

资料库身份 `vaultId` 只接受合法 UUID。SAF 目录 URI 是定位器，分别保存为 `vaultUri` 或配置的 `uri`；身份参数与定位参数不能互换。索引、扫描状态、日记设置、草稿文件键和解析来源键均使用 UUID。目录枚举、原文读写和系统授权使用 URI。

## 身份生命周期

- 首次确认连接时生成 UUID，在同一 DataStore 事务中保存当前 URI、UUID 和 URI → UUID 映射。未确认选择不登记身份。
- 切换到不同 URI 使用独立 UUID；重新连接已知 URI 复用 UUID。解除连接仅清除当前 URI 和 UUID，保留身份映射、设置及本地草稿。
- 配置读取与 UUID → URI 查询均为只读。已连接配置缺少 UUID、身份映射不一致或存储读取失败时明确返回失败，不生成或补写身份。
- Editor、日记设置和草稿必须携带 UUID。恢复使用草稿中保存的 UUID 查询原 URI，核对定位器一致后才继续；不采用当前资料库替代原位置。
- 不自动识别不同 URI 是否对应同一物理目录。

## 存储格式

- App 数据按当前格式创建，不提供版本迁移或兼容读取。切换到本版本前由作者清除 App 数据；Markdown 原文件不属于 App 数据。
- `source-index.db` 使用 schema 3，身份列名为 `vaultId`。数据库通过 `fallbackToDestructiveMigration(dropAllTables = true)` 丢弃并重建索引；重建不访问或改写 Markdown，也不处理独立的分析数据库。
- 草稿与备份显式保存 UUID、原资料库 URI 和目录 URI。文件键根据 UUID 与目标生成；新日记草稿目标统一包含目录 URI 和文件名。
- 草稿使用 JSON，备份使用 `.backup.json`。字段缺失或损坏明确提示，其他有效记录仍可查看、恢复或导出。
- 确认修正的格式如需变更，先导出 JSON 快照再重建。本次未改变分析数据库及确认修正格式。

## 验证

运行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --console=plain` 与 `git diff --check`。

测试覆盖配置只读与失败、UUID 重连复用、设置和索引隔离、索引破坏式重建、UUID 与 URI 分离、草稿损坏隔离、恢复定位核对、备份待完成状态保护以及 Markdown 保存和冲突行为。AndroidTest 源码编译不代表真机流程验收。
