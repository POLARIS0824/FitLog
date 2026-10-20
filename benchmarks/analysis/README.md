# FitLog analysis benchmark

工具与依赖隔离在 `benchmark` 构建变体。正式 analysis 调度、提示词、解析器和数据库格式没有改变。默认是离线回放；只有 `live` 会请求模型。输出目录 `out/` 被 Git 忽略，里面的清单含真实日记，`private-answers/` 含模型回答，请只在本机保留。

## 从仓库根目录运行

需要现有 Windows Android/JDK/Gradle 配置和 Python 3.10+；Python 部分只用标准库。

```powershell
python -X utf8 benchmarks/analysis/benchmark.py inventory --source D:\Obsidian\POLARIS\dairy
python -X utf8 benchmarks/analysis/benchmark.py replay
python -X utf8 -m unittest discover -s benchmarks/analysis -p test_benchmark.py
.\gradlew.bat --no-daemon :app:testBenchmarkUnitTest --tests "com.example.fitlog.benchmark.*"
python -X utf8 benchmarks/analysis/benchmark.py report benchmarks/analysis/out/replay-validated
```

`inventory` 只读所有可见 Markdown（含子目录和空文），冻结相对路径、原始字节 SHA256、FitLog 语义哈希、正文和长度统计。`pilot.json` 固定选择八个不同来源；训练关键词只用于分层选样，不用于过滤完整工作量。重新冻结会创建新资料库 UUID；原始清单和试跑清单应保留在不同目录。一次试跑前后都会校验原文指纹。UTF-8 无法解码、链接越界或文件变化会明确失败。

`replay` 使用 `benchmarks/analysis/fixtures/` 自带的三组固定夹具扩展为八个独立来源，调用真实 `OpenAiDiaryModelSource → JsonDiaryParser → DiaryCandidateValidator → DiaryAnalysisRepository`，HTTP 由 Ktor MockEngine 提供固定回答。它比较当前 `DiaryAnalysisController` 串行链路、轻量 Kotlin 并发 1/2/4/8，以及 ADK 1.3.1 并发 4，共 48 次离线发送。安全测试另外覆盖 429/503、超时/网络失败、拒绝、截断、损坏 JSON、乱序完成、取消、来源变化、实际 Room 写入失败和预算耗尽。

每篇独立 ADK agent/session；`RunConfig(maxLlmCalls=1)`，无工具和记忆服务。自定义 `Model` 桥接到同一个 FitLog 传输，使用规范提示词而非 ADK 加入的路由文本；回放测试要求三个 runtime 的请求正文哈希完全一致。成绩应标为“ADK canonical-model bridge”，不代表框架默认 provider 配置。

## 显式真实试跑

先阅读八篇原文，给本地 `pilot.json` 加上 `checkpoints` 和 `audited: true`。`expectedJson` 是 JSON 字符串。检查点可访问原候选 `$.sessions`、现有校验器展开后的 `$.validated.sessions[0].exercises[0].sets[0].reps.value`，以及数组 `.length`。普通记录和空文检查 `$.metrics.exerciseCount == 0`，避免将合法的空 session 判错。已确定组数的训练检查展开后的次数、重量和单位，让 repsList 与拆分 groups 的等价表达都通过。组数不明确时，可用 `$.metrics.exercises[0].reportedReps` 检查模型报告的次数，不补造展开组数；合法缺失值、UNKNOWN 和核对提示不判错。动作顺序或原名改写会让严格检查点失败，须人工核查，不能直接归因于错误提取。

检查点本身出错时，可离线复核保存的回答，无新增模型请求：

```powershell
python -X utf8 benchmarks/analysis/benchmark.py replay `
  --audit-run benchmarks/analysis/out/live-proxy-pilot `
  --checkpoints benchmarks/analysis/out/inventory/pilot.json `
  --output benchmarks/analysis/out/quality-audit-new
python -X utf8 benchmarks/analysis/benchmark.py report benchmarks/analysis/out/live-proxy-pilot `
  --quality-audit benchmarks/analysis/out/quality-audit-new/quality-audit.json
```

复核仍使用真实生产解析器、校验器和隔离 Room。`quality-audit.json` 关联原 trace、来源与回答 SHA256，原 JSONL 和计时不改写。质量修正若改变首个合格候选，报告保留原边界并将修正后的首次时间标为未知；不从离线耗时伪造线上成绩。重建该报告须带上相同 `--quality-audit`，否则显示原检查点成绩。

```powershell
python -X utf8 benchmarks/analysis/benchmark.py live `
  --dataset benchmarks/analysis/out/inventory/pilot.json `
  --base-url https://api.ppio.com/openai/v1 `
  --model zai-org/glm-5.3-flash `
  --proxy http://127.0.0.1:7890 `
  --previous-run benchmarks/analysis/out/live-pilot `
  --max-requests 49 --output benchmarks/analysis/out/live-manual-restart
```

API Key 通过现有 `FITLOG_BENCH_API_KEY` 环境变量或不回显的交互输入获取。不要写在 argv、JSON 或报告里。完整 `/chat/completions` 地址也可作为输入，入口会移除这一后缀。JVM 的代理必须显式设置；Android 采用设备自身网络，不使用主机代理设置。代理地址不允许包含凭证。

当前计划：最多两次预热；baseline 并发 1、轻量并发 2、ADK 并发 4、轻量并发 4/8，各八篇，共 42 次请求预算。OpenMinis 无独立可运行原生库，所以没有分配请求；剩余预算不挪用。`--previous-run` 将前次的已用请求和预热计入本次上限，超过总授权 50 即拒绝。`--profiles` 允许手动执行仍未执行的配置；程序不会自动重试失败配置。不能用新输出目录解释成新的授权。

终止错误停止整个 live 实验，排队请求不发送，已经在途的请求结算后记录结果。没有自动重试、降级或修复请求。取消则取消所有在途协程；取消请求之后不再调度新 HTTP。输出目录不能覆盖或续跑。失败报告保留未执行数量，禁止把未执行项算成零延迟的成功。

## 输出与计时

`events.jsonl` 是单调时钟的原始事件；`spec.json` 包含复现配置；`summary.json`、CSV 和独立 HTML 是派生报告。报告有并发曲线和可切换指标，原文和回答不会嵌进 HTML。

批次从任务开始调度到所有任务、持久化和来源复核结束（包括数据库关闭）；数据库打开时间独立记录。首次可核对候选要求已经写入 Room、来源版本有效、最低校验通过和已设置的事实检查点通过。它不等于收到首 token，也不等于已经用户确认。

并发配额在 Ktor request pipeline 的 `Before` 阶段等待，先于 `HttpTimeout` 的 Send 阶段启动；许可持有到 `HttpSend` 内完整响应体保存完成。获配额后开始 HTTP 阶段计时，任务数、HTTP 尝试数、runtime 事件数和工具数分别记录。排队时间仅为等待 benchmark 配额，不消耗 120 秒请求超时；引擎内部排队仍包含在 HTTP 阶段。HTTP 时间包含请求传输和完整响应读取，无法拆成服务端推理与网络往返。解析校验、写入、runtime 初始化分开记录。各任务耗时在并发时重叠，不能简单相加当作批次耗时。TTFT 对非流式调用为 null；堆内存是结束时快照，不能当峰值。每轮共享一个原始 Ktor/OkHttp 客户端；只记录允许连接复用，不推断实际复用或服务端缓存命中，缓存 token 仅按 provider 返回记录。

`BenchmarkUiReplayTest` 驱动现有详情与批次 UI 的真实组件，检查解析进度、取消、首个候选后的确认入口、停止后仍可核对已保存结果。它证明组件行为，不证明真实设备帧率或首屏速度。已有生产持久化测试检查重新解析不覆盖用户确认的修正。

## 原生设备回放

使用独立包名 `com.example.fitlog.benchmark`，release 的 R8/资源压缩、debug 签名及 profileable。它有自己的应用数据空间，不能访问生产数据库。设备回放通过同 APK 内的 Android 原生 instrumentation 执行，只接受固定离线输入，不读取 Obsidian 或 API Key。独立 AndroidX Test APK 会引用已被主 APK 优化掉的 Kotlin/Tracing 类；使用同 APK 入口让 R8 看见完整调用链，避免扩大全库 keep。benchmark 的独立 R8 规则避开正式规则中遗留的 ADK 全包 keep；`-checkdiscard` 强制确认不兼容 Ktor 的 Google GenAI HTTP client 已被删除。它仅支持此处自定义 Model 桥接，不证明内置 Google provider 可用。

```powershell
.\gradlew.bat --no-daemon :app:assembleBenchmark
python -X utf8 benchmarks/analysis/benchmark.py replay --device-spec --output benchmarks/analysis/out/device
adb install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb shell mkdir -p /sdcard/Android/data/com.example.fitlog.benchmark/files
adb push benchmarks/analysis/out/device/device-spec.json /sdcard/Android/data/com.example.fitlog.benchmark/files/device-spec.json
adb shell am instrument -w -r -e benchmarkSpec device-spec.json com.example.fitlog.benchmark/com.example.fitlog.benchmark.BenchmarkInstrumentation
adb pull /sdcard/Android/data/com.example.fitlog.benchmark/files/results benchmarks/analysis/out/device-results
```

设备输出包含实际生效的 `spec.json`，拉回后直接运行 `report`。必须看到 `INSTRUMENTATION_CODE: -1`、完整 `run_end` 与各配置的实际结果，不能只按 adb 进程退出码判定通过。设备输出目录不覆盖，重复试验请用不同 `benchmarkOutput` 参数，例如 `-e benchmarkOutput results-run2`，并拉回对应目录。记录设备型号、OS、温度/省电模式、网络与实验时间；保持状态相近。此设备回放是流程与 CPU 开销验收；真实请求入口见下节。不要将 JVM/Node 成绩当作 Android 成绩。

## 上游 pi 单独回放

```powershell
npm --prefix benchmarks/analysis/pi ci --ignore-scripts
npm --prefix benchmarks/analysis/pi test
node benchmarks/analysis/pi/replay.mjs benchmarks/analysis/out/replay-validated/spec.json benchmarks/analysis/out/pi-replay-new 4
```

运行真实上游 `Agent` 1.1.0 与注入的固定事件流，每篇独立 Agent，仅一次 stub 调用，无 HTTP、工具、Kotlin 校验器或 Room。输出 `pi-summary.json`。它仅检查协议、上下文隔离和生命周期。需要 JS 引擎/桥接才能在 Android 运行；目前没有证明这种集成值得引入。

## 原生真实 API 测量准备

2026-10-10 用户已明确测试费用无需作为阻碍。原试跑的 26/50 次记录保持原样；后续阶段显式填写 `--budget-limit` 和本次 `--max-requests`，失败与诊断请求均计数。默认研究上限仍为 50，工具允许显式声明至多 10,000；准备配置暂以 1,500 为整个研究的工程上限，实际分批执行。跨阶段使用 `--previous-run` 传递已用数量；新预算允许更换为完整目录数据集，但模型和 endpoint 必须相同。

先冻结计划；`--prepare-only` 不读取 Key、不构建/安装、不发 API 请求，也不占用手机：

```powershell
python -X utf8 benchmarks/analysis/benchmark.py live `
  --dataset benchmarks/analysis/out/inventory/pilot.json `
  --base-url https://api.ppio.com/openai/v1 --model zai-org/glm-5.3-flash `
  --device --stage connectivity --prepare-only `
  --max-requests 2 --budget-limit 1500 --period native-connectivity `
  --output benchmarks/analysis/out/native-ready-connectivity
```

实际执行需显式去掉 `--prepare-only`，使用新的输出目录，并增加 `--key-file D:\Code\apiKey.txt`。凭证文件只含 Key，它不属于复现配置。工具用系统临时文件单独推送到 benchmark 包目录，设备读入后立即删除，host 最后再次清理；Key 不进入 argv、spec、报告，只拉取本次结果目录。准备成功不证明真实 HTTP 连通。

原生 helper 会先构建独立 R8 APK并安装，记录 APK 哈希与设备状态。正式测量需 USB 连接、解锁亮屏、稳定网络、关闭省电；屏幕保持较低固定亮度，暂时不用手机。helper 检测到睡眠、省电或中等以上热状态时拒绝开测，不自动等待/重试。前台宿主在测量中保持亮屏；点击取消、退出或切换应用会取消测量。host 中断也会停止 benchmark 包，不停止正式 FitLog。测试完成后可正常使用。依据见 [Android 性能测试](https://developer.android.com/training/testing/instrumented-tests/performance) 与 [温度/时钟影响](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview)。

每次命令对应一个真实实验时段：

| `--stage` | 工作量与条件 |
| --- | --- |
| `connectivity` | 八篇清单中的前两篇，单并发；包含空文与长篇，检查 POST、解析与事实检查点；标记为诊断/预热，不排名 |
| `concurrency` | 同一轻量 runtime，并发 1/2/4/8，每批相同八篇；一时段 32 次。所有日记须已建立事实检查点 |
| `runtime --concurrency N` | 轻量与 ADK 使用相同 N，每批八篇；一时段 16 次。N 在并发筛选后填写 |
| `folder --configs lightweight:N adk:N` | 显式入围配置解析冻结完整目录；两个配置一时段 346 次。原文事实抽查与质量覆盖仍需单独审查 |

`--rounds 3` 只是同一 `--period` 内重复三次，不会伪造三个独立时段。正式比较需在实际不同时间段分别执行，填写不同 `--period`，预先选定顺序种子并交错候选；默认每时段一轮。网络/限流等终止错误发生时停止当前实验，保留未执行数量。诊断发现空文编造或其他质量失败时，先解决质量并冻结新协议，再开始正式比较；不自动修复、重试或切模型。

例：并发扫描准备使用 `--stage concurrency --max-requests 32 --budget-limit 1500 --period native-period-1 --order-seed 42 --prepare-only`。固定并发后使用 `--stage runtime --concurrency N --max-requests 16`。配置批次按种子打乱，每批的日记集合与顺序保持一致。runtime 的速度区间只比较相同并发、时段与轮次的轻量/ADK；串行与并发的差异不会记为框架收益。

设备读取冻结快照，原始字节/语义哈希保持在 spec；host 执行前后校验原目录。此测试没有走 SAF，首个可核对结果的来源边界是冻结内容版本。私有原文与回答只在 `out/` 本地保留。真实 UI 帧率和 SAF 体验需独立验收。

## 正式比较与下一阶段

按固定工作量看完整文件夹耗时；固定到达率只检查排队，配置使用 `arrivalIntervalMs`，任务按预定时间入队且延迟从计划到达计算。两种场景分开报告。负载不使用自动重试或定时后台任务。

正式重复实验应随机交错入围配置，固定八篇/整个文件夹和顺序种子，记录独立 `period` 与配对 `round`。`live --order-seed 42` 保留预热在前，按种子打乱完整配置批次的顺序；后续时段用预先选定的不同种子交错候选，顺序写入 spec。已完成的探索试跑发生在此选项加入前，采用固定先后顺序，报告明确列为限制。一次试跑时段不能从八篇日记伪造八个独立实验。多次报告可运行 `report <run1> <run2> <run3> --output <comparison-dir>`；模型、提示词、数据集、源码指纹、代理、平台和超时必须一致，period/round 不能重复。整批耗时以完整批次为单位，按时段做配对 bootstrap 95% 区间；至少三个独立时段才输出区间，三个仍只支持初步推断。并发选择质量通过且吞吐达到最佳点 95% 的最小值。复杂 runtime 只有设备可部署、质量合格且速度提升区间下界超过 10% 才通过性能门槛；最终决定还需集成成本证据。

报告生成两个配置、三个配对完整文件夹时段的请求/token 估算：173×2×3=1038 次。原试跑上限仍是 50；后续预算显式声明，原生分阶段执行器已加入。没有价格或 token 数据就显示未知，不猜成本。提示词压缩、schema/JSON、流式、连接策略及多篇合并分别做后续实验，需逐项固定其他因素和新的 extractor 版本；没有在本阶段擅自改变业务协议。合并时必须逐篇标识、恢复来源且禁止跨篇借用事实。

方法和来源见 [RESEARCH.md](RESEARCH.md)；当前实测结果与限制见 [RESULTS.md](RESULTS.md)。
