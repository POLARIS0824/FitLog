# 整篇日记提取契约

本步提供来源身份、内容版本、类型化提取结果、适配器内部 JSON 解码、证据校验和逐组展开。未接入真实模型、网络、数据库、任务调度、缓存、身份持久化或确认界面。测试模型源只返回预先写好的 JSON 或失败类型，不证明自然语言提取质量。

## 业务接口与成功、失败

```kotlin
val input = DiaryParseInput.fromSnapshot(
    sourceKey = SourceKey(vaultId, "daily/2026-09-30.md"),
    rawText = snapshot.text,
    extractorVersion = "extractor-v1",
)
when (val result = parser.parse(input)) {
    is DiaryParseResult.Success -> {
        val candidate = result.analysis
        // 可进入候选复用；问题仍需核对，所有候选仍需用户确认。
    }
    is DiaryParseResult.Failure -> {
        val reason = result.reason
        // 明确失败，不作为空训练缓存；重试由后续协调器安排。
    }
}
```

`DiaryParser.parse(input): DiaryParseResult` 是业务层入口。成功表示顶层响应可解码且符合本版顶层契约，不表示数据完整、解释正确或已经确认。

- `Success(analysis)`：根对象包含受支持的数值型 `schemaVersion` 和 `sessions` 数组；可选 `issues` 出现时必须是数组。局部场次、动作、组块或问题条目错误被隔离，有效兄弟条目和原位置保留。`analysis.hasErrors` 只标识局部 ERROR，不承担整个提取操作的成功／失败分类。
- `Failure(reason)`：`MALFORMED_JSON`、`INVALID_TOP_LEVEL`、`UNSUPPORTED_SCHEMA`、`MODEL_REFUSAL`、`TIMEOUT`、`NETWORK_ERROR`。没有空的成功结果来替代失败，也不附带原始 JSON。
- 只有 `Success` 可以进入后续候选复用。`Failure` 不得进入复用，不代表没有训练。合法空训练是成功，但只表示未提取到训练，不表示休息日；有局部错误时更不能据此断言没有训练。
- 预期请求失败由模型适配器明确报告。`CancellationException` 原样抛出；请求超时须由适配器明确识别，不能把协程取消吞掉后当作超时。编程异常不兜底转换为空结果。

JSON 获取、解码与校验放在 `analysis.adapter` 包。原始 JSON 仅在适配器处理期间存在，不持久化、不穿过业务层；`DiaryAnalysis` 不含 rawJson。未知 JSON 字段忽略，不提供原始 JSON 的回放或检查入口。测试 JSON 文件是适配器样例，不是运行时存储。

当前 app 只有一个模块，因此 `internal` 并不能阻止同模块业务代码调用适配器；业务层只依赖类型化入口属于项目约定，未来拆模块后才能由编译器强制。

## 来源身份与四字段复用键

`SourceKey(vaultId, relPath)` 保存两个独立字段，不拼接为字符串。vaultId 是调用方提供的应用资料库身份，不是 SAF URI；本步不生成或持久化它。

relPath 使用实际文件名构成的根相对路径：

- 两字段非空，分隔符为 `/`。
- 拒绝反斜杠、开头 `/`、空段、`.` 和 `..`，包括连续 `/` 或末尾 `/`。
- 不修剪空白，不转换大小写，不做 NFC/NFD 归一化；例如 `é.md` 与 `e\u0301.md` 保持不同来源。
- 改名、移动或 Unicode 形式变化会改变来源键；重新连接和人工重新关联留给后续实现，不自动猜测映射。

`DiaryParseKey(sourceKey, contentHash, hashVersion, extractorVersion)` 是整篇文件候选的复用身份。输入及成功结果携带同一个键。来源、内容或任一版本变化都会改变键。失败结果不能用这个键占据成功候选缓存。

extractorVersion 由调用方提供，覆盖模型配置、提示词、模型输出契约和本地后处理规则，包括重量沿用、日期和证据匹配规则。任一变化都必须更新版本。旧候选因此失效；由于不存原始 JSON，将来可能需重新调用模型。候选属于可丢弃草稿，版本变化不得自动清除用户确认的修正。

模型输出的 schemaVersion 与未来候选存储格式版本独立。本次仅有模型 schema v1，不引入存储格式或缓存实现。

## 共享内容哈希 v1

共享工具位于 `data.hash`，版本为 `CONTENT_TEXT_HASH_VERSION = 1`：

1. 去掉一个开头 BOM。
2. 先把 CRLF 换成 LF，再把剩余单独 CR 换成 LF。
3. 将结果按 UTF-8 编码，计算 SHA-256，输出 64 位小写十六进制。

不去空白、不替换训练符号、不折叠 Unicode、不去中间 BOM。开头两个 BOM 只去掉第一个。后续算法变化必须升 hashVersion，不能改写 v1。

`ContentTextSnapshot.fromRawText(rawText)` 一次生成归一化全文、哈希和版本，构造器私有。内部摘要函数只处理编码和 SHA-256，不做归一化。输入工厂使用这个结果，不能把已归一化全文再次传入会去 BOM 的工厂。重复以同一原始文本构建，文本与哈希均一致；这不表示去一个 BOM 的归一化操作可以在自己的输出上重复执行。

模型看到的全文、证据匹配参照和哈希编码文本保持一致。固定整篇输入，没有 TextSelection，也不自动过滤私人内容。空文件允许建立输入快照。未来生产模型源只收到归一化全文，不收到来源键、哈希或应用身份。

`MarkdownDocumentRepository.fingerprint(bytes)`、Source Index 和编辑器仍使用原字节指纹，用于检查写回冲突。不能将其替换为内容哈希，也不能互相比较。本步未改变 Markdown 原文件、编辑器字节指纹或索引实现。

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

组块使用 count + reps 或 repsList；同时提供 count 和 repsList 时长度必须一致，不能再提供标量 reps。组数、次数为正整数，重量为有限非负数。缺失数值保留 null，不编造一组或次数。unit 为 UNKNOWN / KG / LB；basis 为 UNKNOWN / PER_SIDE / TOTAL / BODYWEIGHT / ADDED / ASSISTED。未知枚举降为 UNKNOWN，由本地问题提示待核对。

inferredFields 标识 weight、unit、basis、reps、count 等推断字段；未知字段名仍报告待核对。模型 issues 使用 path 和 question；本地校验问题使用 path、代码和严重程度，供未来 UI 用字符串资源翻译。

## 一文件一天与日期

一个文件对应一天，是一个确认单位。同一天可以有多个训练场次，共享同一个文件解析键。日期、场次、动作或摘录都不是来源身份。

场次 date 只是模型报告的候选日期，使用严格的 `yyyy-MM-dd` 形状并检查真实日历有效性。`2026-13-40`、`2026-02-30`、非闰年 2 月 29 日等置空并报告 `INVALID_DATE / ERROR`，保留该场次其他数据，属于成功结果中的局部错误。缺失日期报告待核对，不补当前日期。

出现多个不同的有效非空候选日期时，相关日期报告 `DATE_CONFLICT / REVIEW`。保留场次供核对，不把它们当作已确定的多日训练，也不自动选择第一个日期或拆分日记。

本次仅检测模型报告日期之间的冲突，尚不检测单个日期与文件名不符。最终日记日期和文件名优先流程属于后续提交，须在确认和分析使用日期前落实。

## 证据匹配与偏移

整篇片段固定为 diary。摘录和组块匹配只将 CRLF/CR 统一为 LF；不裁剪首尾空白、不折叠 Unicode、不额外去除摘录开头的 BOM。组块 rawText 以归一化换行后的形式在动作摘录中顺序匹配，模型报告的 rawText 本身保留。

- 摘录唯一匹配：返回归一化全文的 UTF-16 起止下标，包含起点、不包含终点。emoji 使用 Kotlin String 的 UTF-16 下标，不是码点或字节偏移。
- 摘录出现多次：`AMBIGUOUS_EVIDENCE / REVIEW`，保留动作、组块和逐组候选，两个偏移均为 null。不选第一处、不按摘录去重。
- 摘录不存在或片段 ID 不正确：局部 ERROR，隔离该动作；有效兄弟条目仍保留。

偏移只针对归一化全文，不能直接用于原始 Markdown 高亮；原始坐标映射留给后续。严格匹配可能因模型多加或漏掉空格而拒绝原本正确的候选。接入真实模型后用样本复核规则；若放宽匹配，须更新 extractorVersion。

证据存在性只证明摘录出现，不证明重量、动作名、次数或日期的解释正确。重新解析不得静默覆盖用户确认的修正。

## 重量沿用与逐组展开

同一动作的后续组块沿用前置重量，直到出现新重量。例如 `40kg 2×7＋1×4` 展开为 40kg×7、40kg×7、40kg×4；最后一组标为 INHERITED 并指向组块 0。

沿用不跨动作或场次。新重量重置单位与口径上下文，缺少单位不借用前一重量的单位；单侧重量不自动乘二。推断值保留 INFERRED 标志，沿用推断重量时保留 INHERITED 和 inferred 两种信息。

只知道 count 时展开对应组数且 reps 保持 null；只有 reps 不编造一组。“练腹”保留动作、空组列表和待核对提示。损坏、无效或证据不匹配的组块中断重量沿用。每个动作最多展开 1000 组。

原组块位置和组内编号保留；weightKg 只换算单位，原重量和口径仍在类型化候选中。

## 验证范围

运行 `./gradlew.bat testDebugUnitTest assembleDebug` 和 `git diff --check`。

测试覆盖顶层失败与合法空成功、预期请求失败和取消传播、局部隔离、来源及版本、单次归一化、严格日期和跨日冲突、重复摘录、多行换行及 UTF-16 偏移、重量沿用和逐组展开。

user-sample.md 是用户提供的摘录，预期 JSON 是手写候选；constructed-boundaries.md 是跨日冲突构造样例，不是有效多日日记。测试不验证真实模型质量、真实网络、SAF provider、候选缓存、确认事务或分析数据库。
