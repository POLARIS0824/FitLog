# 整篇日记提取契约

解析层提供来源身份、内容版本、类型化提取结果、适配器内部 JSON 解码、证据校验和逐组展开。分析数据库、直接挂起解析和当前确认事务已实现，见 [日记分析数据库](diary-analysis-database.md)。Ktor 模型 adapter 已实现；配置页、手动解析和确认界面尚未接入，真实模型未验收。资料库 UUID 已由 VaultPreferences 持久化，文件索引也使用该身份。测试模型源只返回预先写好的 JSON 或失败类型，不证明自然语言提取质量。

## 业务接口与成功、失败

```kotlin
val input = DiaryParseInput.fromSnapshot(
    sourceKey = SourceKey(vaultId, "daily/2026-09-30.md"),
    snapshot = snapshot,
    extractorVersion = "extractor-v1",
)
when (val result = parser.parse(input)) {
    is DiaryParseResult.Success -> {
        val candidate = result.analysis
        // 可保存候选；问题仍需核对，所有候选仍需用户确认。
    }
    is DiaryParseResult.Failure -> {
        val reason = result.reason
        // 明确失败，不作为空训练缓存；仅在主动重试时再发起请求。
    }
}
```

`DiaryParser.parse(input): DiaryParseResult` 是业务层入口。成功表示整篇响应可解码且符合本版结构契约，不表示数据完整、解释正确或已经确认。

- `Success(analysis)`：整篇 JSON 能整体解码；根对象包含受支持的数值型 `schemaVersion` 和 `sessions` 数组，可选 `issues` 出现时必须是数组。结构错误使整篇失败；本地语义校验仍保留有效条目和待核对问题。`analysis.hasErrors` 只标识局部 ERROR，不承担整个提取操作的成功／失败分类。
- `Failure(reason)`：`MALFORMED_JSON`、`INVALID_TOP_LEVEL`、`UNSUPPORTED_SCHEMA`、`INVALID_RESPONSE`、`MODEL_REFUSAL`、`TIMEOUT`、`NETWORK_ERROR`。没有空的成功结果来替代失败，也不附带原始 JSON。
- `Success` 保存为候选；`Failure` 保存为失败记录，不代表没有训练。合法空训练是成功，但只表示未提取到训练，不表示休息日；有局部错误时更不能据此断言没有训练。
- 预期请求失败由模型适配器明确报告。`CancellationException` 原样抛出；请求超时须由适配器明确识别，不能把协程取消吞掉后当作超时。编程异常不兜底转换为空结果。

JSON 获取、解码与校验放在 `analysis.adapter` 包。`DiaryParser` 仍只返回类型化结果，`DiaryAnalysis` 不含 rawJson。适配器另外实现内部 `RecordingDiaryParser.execute`，以 `DiaryParseExecution` 向持久化层提供类型化结果和未经改写的原始响应；收到响应但解码失败时也保留原始响应。分析库分别保存原始响应与已校验候选，未知模型字段仍不参与业务解析。当前不提供跨版本回放入口。

当前 app 只有一个模块，因此 `internal` 并不能阻止同模块业务代码调用适配器；业务层只依赖类型化入口属于项目约定，未来拆模块后才能由编译器强制。

## 来源身份与四字段内容版本键

`SourceKey(vaultId, relPath)` 保存两个独立字段，不拼接为字符串。vaultId 必须是小写、标准格式的 UUID；拒绝 SAF URI、任意标签及缩写 UUID。首次连接时由 VaultPreferences 生成随机 UUID 并持久化，配置读取只校验现有 UUID 与映射，不补写旧身份。同一 URI 重新连接、切换回来或解除后重连均复用 UUID。UUID 与 URI 的映射独立于可重建的 Room 索引；URI 只负责 SAF 定位与权限检查。

relPath 使用实际文件名构成的根相对路径：

- 两字段非空，分隔符为 `/`。
- 拒绝反斜杠、开头 `/`、空段、`.` 和 `..`，包括连续 `/` 或末尾 `/`。
- 不修剪空白，不转换大小写，不做 NFC/NFD 归一化；例如 `é.md` 与 `e\u0301.md` 保持不同来源。
- 改名、移动或 Unicode 形式变化会改变来源键；重新连接和人工重新关联留给后续实现，不自动猜测映射。

`DiaryParseKey(sourceKey, contentHash, hashVersion, extractorVersion)` 关联整篇文件候选的来源和内容版本。输入及成功结果携带同一个键。来源、内容或任一版本变化都会改变键；失败只记录当次尝试。

extractorVersion 由调用方提供，覆盖模型配置、提示词、模型输出契约和本地后处理规则，包括重量沿用、日期和证据匹配规则。任一变化都必须更新版本。旧候选因此失效；保存原始响应并不允许跨提取器版本自动复用。未来重新校验还需要匹配的原文内容版本。候选属于可重算草稿，版本变化不得自动清除用户确认的修正；当前确认引用的解析记录必须保留。

模型输出的 schemaVersion 与候选存储格式版本独立。模型 schema 为 v1；候选存储格式为 v2。每次显式解析执行一次请求，不自动复用旧成功结果。

## 共享内容哈希 v1

共享工具位于 `data.hash`，版本为 `CONTENT_TEXT_HASH_VERSION = 1`：

1. 去掉一个开头 BOM。
2. 先把 CRLF 换成 LF，再把剩余单独 CR 换成 LF。
3. 将结果按 UTF-8 编码，计算 SHA-256，输出 64 位小写十六进制。

不去空白、不替换训练符号、不折叠 Unicode、不去中间 BOM。开头两个 BOM 只去掉第一个。后续算法变化必须升 hashVersion，不能改写 v1。

`ContentTextSnapshot.fromRawText(rawText)` 一次生成归一化全文、哈希和版本，构造器私有。内部摘要函数只处理编码和 SHA-256，不做归一化。输入工厂使用这个结果，不能把已归一化全文再次传入会去 BOM 的工厂。重复以同一原始文本构建，文本与哈希均一致；这不表示去一个 BOM 的归一化操作可以在自己的输出上重复执行。

模型看到的全文、证据匹配参照和哈希编码文本保持一致。固定整篇输入，没有 TextSelection，也不自动过滤私人内容。空文件允许建立输入快照。生产模型源只收到归一化全文，不收到来源键、哈希或应用身份。

`MarkdownDocumentRepository.fingerprint(bytes)` 和编辑器仍使用原字节指纹检查写回冲突，与内容哈希用途不同。Source Index 不保存指纹；每次扫描逐篇读取。Markdown 原文件及编辑器的冲突检测保持现状。

## 模型 JSON v1 与候选字段

```json
{
  "schemaVersion": 1,
  "sessions": [{
    "date": null,
    "notes": null,
    "exercises": [{
      "rawName": "杠铃上斜卧推",
      "evidence": {
        "segmentId": "diary",
        "quote": "杠铃上斜卧推：40kg 2×7＋1×4"
      },
      "groups": [
        { "rawText": "40kg 2×7", "weight": 40, "unit": "KG", "basis": "TOTAL", "count": 2, "reps": 7 },
        { "rawText": "1×4", "count": 1, "reps": 4 }
      ]
    }]
  }],
  "issues": []
}
```

每个场次包含 exercises。动作保留原名、备注、摘录和原始组块，本步不绑定标准动作 ID。字面值也是候选，模型标为明确不等于用户已确认。

组块使用 count + reps 或 repsList；同时提供 count 和 repsList 时长度必须一致，不能再提供标量 reps。组数、次数为正整数，重量为有限非负数。缺失数值保留 null，不编造一组或次数。unit 为 UNKNOWN / KG / LB；basis 为 UNKNOWN / PER_SIDE / TOTAL / BODYWEIGHT / ADDED / ASSISTED。缺失枚举使用 UNKNOWN；非法枚举使整体解码失败，不猜测其含义。

inferredFields 标识 weight、unit、basis、reps、count 等推断字段；未知字段名仍报告待核对。模型 issues 使用 path 和 question；本地校验问题使用 path、代码和严重程度，详情 UI 用字符串资源翻译。

## 一文件一天与日期

一个文件对应一天，是一个确认单位。同一天可以有多个训练场次，共享同一个文件解析键。日期、场次、动作或摘录都不是来源身份。

场次 date 只是模型报告的候选日期，使用严格的 `yyyy-MM-dd` 形状并检查真实日历有效性。`2026-13-40`、`2026-02-30`、非闰年 2 月 29 日等置空并报告 `INVALID_DATE / ERROR`，保留该场次其他数据，属于成功结果中的局部错误。缺失日期报告待核对，不补当前日期。

出现多个不同的有效非空候选日期时，相关日期报告 `DATE_CONFLICT / REVIEW`。保留场次供核对，不把它们当作已确定的多日训练，也不自动选择第一个日期或拆分日记。

解析器仅检测模型报告日期之间的冲突。审阅入口使用 `suggestDiaryDate` 严格识别三种现有文件名日期格式，并检测模型日期与文件名的冲突。确认请求必须明确携带最终日记日期，不补今天；所有确认场次归属于该日期。审阅 UI 后续接入。

## 证据匹配

整篇片段固定为 diary。摘录和组块匹配只将 CRLF/CR 统一为 LF；不裁剪首尾空白、不折叠 Unicode、不额外去除摘录开头的 BOM。组块 rawText 以归一化换行后的形式在动作摘录中顺序匹配，模型报告的 rawText 本身保留。

- 摘录唯一匹配：保存归一化后的 segmentId 和 quote，不保存起止偏移。
- 摘录出现多次：`AMBIGUOUS_EVIDENCE / REVIEW`，保留动作、组块和逐组候选，不按摘录去重，也不产生坐标。
- 摘录不存在或片段 ID 不正确：局部 ERROR，隔离该动作；有效兄弟条目仍保留。

详情仅展示旧版本摘录，不高亮当前原文。严格匹配可能因模型多加或漏掉空格而拒绝原本正确的候选。接入真实模型后用样本复核规则；若放宽匹配，须更新 extractorVersion。

证据存在性只证明摘录出现，不证明重量、动作名、次数或日期的解释正确。重新解析不得静默覆盖用户确认的修正。

## 重量沿用与逐组展开

同一动作的后续组块沿用前置重量，直到出现新重量。例如 `40kg 2×7＋1×4` 展开为 40kg×7、40kg×7、40kg×4；最后一组标为 INHERITED 并指向组块 0。

沿用不跨动作或场次。新重量重置单位与口径上下文，缺少单位不借用前一重量的单位；单侧重量不自动乘二。推断值保留 INFERRED 标志，沿用推断重量时保留 INHERITED 和 inferred 两种信息。

只知道 count 时展开对应组数且 reps 保持 null；只有 reps 不编造一组。“练腹”保留动作、空组列表和待核对提示。语义无效或证据不匹配的组块中断重量沿用；结构损坏的组块使整篇解码失败。每个动作最多展开 1000 组。

原组块位置和组内编号保留；weightKg 只换算单位，原重量和口径仍在类型化候选中。

## 验证范围

运行 `./gradlew.bat testDebugUnitTest assembleDebug` 和 `git diff --check`。

测试覆盖顶层失败与合法空成功、预期请求失败和取消传播、整体解码失败、语义问题隔离、来源及版本、单次归一化、严格日期和跨日冲突、重复摘录、多行换行及 emoji 证据、重量沿用和逐组展开。

user-sample.md 是用户提供的摘录，预期 JSON 是手写候选；constructed-boundaries.md 是跨日冲突构造样例，不是有效多日日记。分析库测试另覆盖直接解析、取消、确认事务及磁盘重开持久化。测试不验证真实模型质量、真实网络、真实 SAF provider 或尚未接入的审阅 UI。
