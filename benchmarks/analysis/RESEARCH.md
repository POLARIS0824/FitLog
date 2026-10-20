# 调研与选型依据

原生构建遇到三个真实集成障碍：ADK 的资源索引重复、桌面 XML parser 与 Android 平台重复，以及 GenAI 1.4 HTTP client 的旧 Ktor Timeout ABI。修复全部限定在 benchmark：合并许可证/移除索引、排除桌面 XML parser、使用独立 R8 规则移除未使用 Google HTTP client，并用 `-checkdiscard` 检查它确实被删除。FitLog 原始 Ktor 3.4.3 没有降级；Google 内置 provider 未获验证。

实机验收发现了额外的测试边界：独立 AndroidX Test APK 引用了主 APK 已移除的 Trace、Lambda、LazyKt，构建通过仍不能启动测试。逐个保留类没有解决整体边界，因此改为 benchmark APK 内的 Android 原生 instrumentation，不保留整个 Kotlin 或 AndroidX Test 库，也不关闭 R8。此选择参考 [AOSP 对外部测试 APK 依赖与同包测试的说明](https://source.android.com/docs/core/perf/r8-optimization)，实际效果以设备回放为证。源码指纹现包含 manifest 与 ProGuard 文件；失败启动日志单独保留，没有作为候选性能成绩。

试跑还发现 ADK history rewriter 会删除空白 user turn，导致自定义 Model 收不到空日记。桥接现在只在原始输入为空白且 user turn 缺失时恢复冻结原文；非空输入仍要求严格一致。空文、一次 HTTP 请求、正文哈希一致以及取消清理已通过离线 Runner 测试。Oct 10 修复后尚未新增 ADK 付费调用；Oct 11 同并发 2 的完整 Android 线上配对已完成，八篇通过且正文一致，见 [RUNTIME_20261011.md](RUNTIME_20261011.md)。这新增了线上可行性证据，仍没有独立重复区间或最终速度结论。

这些是方法参考与可行性审查，不是第三方项目的性能成绩。固定源码已只读下载到本地忽略目录 `out/upstream/`，没有复制第三方业务循环到 FitLog。

| 来源与版本 | 从代码/资料确认的内容 | 本实验采用方式 |
| --- | --- | --- |
| [openGym pipeline.js](https://github.com/DuarteSantos8/openGym/blob/b1cff630b3c30d8bad1f698a264d52314a029855/api/coach/core/pipeline.js)，`b1cff630…` | `attemptOnce` 分开请求、协议解析与业务校验；`runPipeline` 可以再次请求修复 | 采用分阶段观测、统一校验的结构。FitLog 不引入修复请求或失败降级 |
| [ADK Kotlin](https://github.com/google/adk-kotlin/tree/d3d7311099de18b09dd4992e86b8976cbe4ea610)，Android Maven `1.3.1` | `LlmAgent`、自定义 `Model`、独立 Session、Runner 生命周期可原生接入 | 实际编译/回放 Runner；使用 FitLog 原始传输，限制一次模型调用，避免默认 provider 差异 |
| [OpenMinis Android ChatViewModel](https://github.com/OpenMinis/OpenMinis/blob/b4c0661d5631ebab4d1a2e6f3fd4c805d4030a6c/src/android/app/src/main/java/com/openminis/app/ui/chat/ChatViewModel.kt)，`b4c0661d…` | `runAgentLoop` 是 ViewModel 的 private 方法，调用 provider/history/tool/UI/compaction 等状态。文件约 1,012,626 字节；`MAX_CONCURRENT_TOOLS=10` 限制工具执行 | 当前无法作为独立库隔离；不给性能分数。若将来抽取，只能标为改编实现，10 不是 HTTP 模型请求并发 |
| [上游 pi-agent-core](https://github.com/earendil-works/pi/tree/f1b2e77f5b13b2a199b1052cb79c235451afe7d7/packages/agent)，npm `@earendil-works/pi-agent-core` / `pi-ai` `1.1.0` | TypeScript/Node 的真实 `Agent` 可注入 `streamFn`，并支持事件订阅、abort 与 waitForIdle | 固定 lockfile，单独 Node 回放验证协议与生命周期。它与 OpenMinis 的 Android Kotlin loop 分开评估，不赋予 Android 性能分数 |

本地研究副本 SHA256：openGym pipeline `ddfb4808976e988f89d81216858e2c7a03db6818e274bc29709e7158a913073f`；OpenMinis ChatViewModel `9e5a01fc5bd96dc743985ffa77c99a0d2c176077f705b19172af205067850df7`；ADK README `37741f56b84be0120fc0c967dad93ca499b53a3d2417da0885ca316350ce3416`。npm 实际发布包的精确版本与完整性在 `pi/package-lock.json` 中固定；不要把 Git 源码 snapshot 当成发布包的同一构建。

[MLCommons Endpoints](https://mlcommons.org/benchmarks/endpoints/) 将吞吐、并发和延迟画成曲线，帮助识别吞吐平台及体验代价。本工具借用这种观察方式，但没有运行 MLPerf，也不宣称 MLPerf 合规。主指标改为 FitLog 固定文件夹的批次完成时间与有效候选吞吐。

[k6 open/closed model 文档](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/) 解释完成驱动的负载为何会掩盖持续到达时的排队。固定文件夹适合固定工作量测试；`arrivalIntervalMs` 另测固定到达率，延迟从预定到达时间计算。两者不能混为一个吞吐排名。

[Kalibera/Jones: Rigorous Benchmarking in Reasonable Time](https://kar.kent.ac.uk/33611/) 强调重复层次和不确定性。这里整批差值按批次处理，跨配置配对，并按独立时段重采样；日记内请求不能当作独立批次。八篇试跑的长尾只是描述，至少三个独立时段才给初步置信区间，更可靠的正式测量需更多时段。

[Android performance testing](https://developer.android.com/training/testing/instrumented-tests/performance) 支持在受控设备与合适构建下验证性能。独立 benchmark APK 继承 release 的压缩设置，运行数据与正式包隔离；Robolectric/Node 只证明各自环境。实机、网络、热状态和 UI 响应验收仍单独记录。

默认选型保留直接调用或轻量 Kotlin。它们与单篇、一次调用、来源可追踪的提取任务相符。ADK 仅在设备集成通过、质量合格且配对速度提升的 95% 区间下界超过 10% 时，才能凭性能获选。OpenMinis 的复杂循环适合更广的工具任务，但本研究尚未找到可独立复用的原生模块；pi 需要额外 Android JS 运行环境与数据桥接。这些是基于源码结构的集成判断，不是速度测量结论。

## 客户端并发与观测边界

固定版本的 [Ktor 3.4.3 OkHttpEngine](https://github.com/ktorio/ktor/blob/3.4.3/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkHttpEngine.kt) 创建独立 OkHttp Dispatcher；FitLog 当前配置没有覆盖它。解析后的 OkHttp 版本为 5.3.2，其 [Dispatcher 源码](https://github.com/square/okhttp/blob/parent-5.3.2/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt) 默认全局异步调度上限 64、同一 host 上限 5，超出部分在客户端等待。这是源码确认的默认行为，没有单独观测实际 socket 或服务端在途数。

benchmark 的信号量在 Ktor request pipeline `Before` 阶段等待配额，持有至 `HttpSend` 中完整响应保存完成；`max_inflight_http` 记录获配额的在途请求尝试，包含引擎排队，不能等同于服务器并发。报告的 `queue_ms` 只测 benchmark 配额等待，OkHttp 内部排队落在 `http_ms` 中。因回调和响应体读取边界不同，也不能简单把实际并发认定为恰好五个。并发 8 曲线首先回答“当前传输配置下是否更快”；出现平台时，客户端调度、provider 限制与模型波动仍需区分。若进一步实验 Dispatcher，另冻结传输配置、源码指纹与报告，不改写原曲线或生产默认。

## 2026-10-11 排队与超时边界修正

真实扫描暴露了原实现的边界错误：把信号量等待放在 `HttpSend` 拦截器内时，Ktor 的请求定时器已在外层启动。并发 1 最后一篇排队 118.639 秒，HTTP 阶段仅 1.367 秒便超时，整批停在约 120 秒。该失败不能解释为 provider 的单次 HTTP 请求耗尽了 120 秒。

固定版本的 [HttpTimeout 源码](https://github.com/ktorio/ktor/blob/3.4.3/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/HttpTimeout.kt) 在 Send hook 启动取消定时器；[HttpRequestPipeline 源码](https://github.com/ktorio/ktor/blob/3.4.3/ktor-client/ktor-client-core/common/src/io/ktor/client/request/HttpRequestPipeline.kt) 定义 Before 早于 Send。配额等待因此移到 Before；仍用同一个 canonical client、120 秒 HTTP 超时、一次请求与完整响应保存。新增回放使用真实 Ktor 定时器：三个 120 毫秒响应、单并发、每请求 200 毫秒超时，最后一篇排队超过 200 毫秒仍应成功。另测 400 毫秒响应与 100 毫秒超时，验证真实超时后排队请求不再发送。Ktor 嵌套取消异常中携带的 timeout cause 也在释放许可前关闭配额入口；普通用户取消继续传播。

修正只在 benchmark，不修改生产 analysis。旧原始事件保留，新的源码指纹和 `timeout_scope` 明确计时边界；不能将旧扫描与修复后成绩配对或合并排名。空文编造三条训练动作是独立的模型质量失败，仍然有效，不随计时修正消失。
