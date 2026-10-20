# 2026-10-11 原生真实 API 测量准备

修复后的实机并发曲线初选并发 2，第一时段同并发 runtime 比较也已完成：ADK 59.985 秒、轻量 75.963 秒，两者均八篇通过。只有一个有效配对时段，没有最终最佳并发或 runtime 结论。下一独立时段配置已冻结，尚未发请求。生产调度和原文未修改；结果见 [RUNTIME_20261011.md](RUNTIME_20261011.md) 与 [VALIDATION_20261011.md](VALIDATION_20261011.md)。

## 已完成

- benchmark 专用前台宿主、取消与离开结束测量、独立包与数据库、显式真实请求入口。
- 凭证读取、设备使用前删除与 host 收尾清理；Key 不进入命令参数、复现配置或报告。使用本机仓库外文件 `D:\Code\apiKey.txt`。
- 26 个 Kotlin benchmark 测试、15 个 Python 测试和独立 release 压缩 APK 构建通过；历史实机固定响应 48 次全部通过检查点。
- Oct 10 真实诊断两次：空文 HTTP 50.667 秒，长篇日记 HTTP 2.946 秒，两篇通过，标记为预热不参与排名。
- Oct 11 先完成 32 次诊断，修正“配额排队消耗 HTTP 超时”的计时错误后，在新的闲置窗口完成 32 次。旧诊断数据不合并排名。修复后 c1/c2/c4 为 8/8，c8 为 7/8，空文编造的质量问题仍存在。
- 同并发 2 的 ADK/轻量比较 16 次全部完成、全部通过现有检查点；实际请求正文逐篇相同。ADK 的较短整批耗时只是单时段差值，首个可核对结果更晚；输出、缓存与接纳顺序仍有差异。
- 真实请求累计 108 次，包含之前尝试及失败请求；研究工程上限显式设为 1,500，分阶段设置本次上限，不自动调用剩余预算。

最新 runtime 配对、设备状态、JSONL、私有回答、汇总和 HTML 见 `out/native-runtime-20261011-c2-period-1/`；完整并发曲线见 `out/native-concurrency-20261011-fixed/`。原诊断与离线记录仍保留；[RESULTS.md](RESULTS.md) 区分平台、日期和源码版本。

## 下一批：第二独立时段的 runtime 比较

已用 `--prepare-only` 冻结 `out/native-ready-runtime-c2-period-2/plan.json`，没有读取凭证或发请求。轻量 runtime 与 ADK 1.3.1 自定义 Model 桥接分别以并发 2 处理同一八篇，共 16 次；69 个检查点复用于各配置。种子 44 的配置顺序为轻量、ADK，逻辑日记集合与顺序固定，实际 HTTP 接纳顺序另记。前序实际尝试 108，本批即使失败也最多尝试 16。源码指纹与最新有效测量一致。

下列命令会真实执行，需要一个新的手机闲置窗口。它重新生成相同受控计划到新的输出目录，已冻结的准备目录不会被覆盖；若修改 benchmark 代码，应重新冻结和检查源码指纹。

```powershell
python -X utf8 benchmarks/analysis/benchmark.py live `
  --dataset benchmarks/analysis/out/inventory/pilot.json `
  --base-url https://api.ppio.com/openai/v1 `
  --model zai-org/glm-5.3-flash `
  --device --device-serial 344f7115 `
  --stage runtime --concurrency 2 --max-requests 16 --budget-limit 1500 `
  --previous-run benchmarks/analysis/out/native-runtime-20261011-c2-period-1 `
  --key-file D:\Code\apiKey.txt `
  --period native-runtime-period-2 --order-seed 44 `
  --output benchmarks/analysis/out/native-runtime-c2-period-2
```

保持 USB 连接、解锁亮屏、稳定网络和关闭省电，亮度固定且较低；下一批预留约五到十分钟，慢响应可能更久。测量期间暂时不使用手机。宿主保持亮屏；取消、切换应用或离开宿主会取消，已保存结果保留。工具拒绝睡眠、省电或热状态达到 MODERATE 的设备，不自动等待或重试。当前测量窗口已结束，手机可正常使用；新批次需新的闲置窗口。

## 如何做出选择

1. 本次修复后 c1/c2/c4/c8 曲线完整；c8 空文编造使其不合格，c2 达到合格最佳吞吐 c4 的 95.6%，故初选 2。不能将一个时段或八篇长尾作为稳定排名。
2. 第一配对时段已经完成。继续以 `--stage runtime --concurrency 2 --max-requests 16` 在同一设备比较轻量与 ADK，同一八篇、模型、协议、传输、校验与并发。每批使用最新实际运行作为 `--previous-run`，当前为 108，不能沿用历史计数。
3. 至少再补两个真实独立时段，填写不同 `--period` 和预先选定的种子；不同标签本身不构成独立测量。`--rounds 3` 仅表示同一时段内重复。至少三个独立时段才输出初步 95% 区间，更可靠结论需要更多时段。ADK 只有可部署、质量通过且速度提升区间下界超过 10% 才凭性能入选；并发曲线也需独立重复。
4. 用入围配置验证完整 173 篇目录，再分别测试提示词、输出协议、连接策略等因素。两个配置、三个完整时段为 1,038 次请求；完整目录的事实抽查与标注覆盖需继续审查，不能用空结果获得速度优势。

发生终止错误会停止当前实验，失败也扣请求额度，未执行量保留；质量未通过的配置不参与推荐。没有自动重试、修复请求或模型切换。

## 证据限制

Android 读取冻结内容快照，host 前后核对原文件；本测量没有走 SAF。首次可核对候选以 Room 写入、冻结来源版本和检查点通过为边界，不能证明真实业务 UI 帧率。设备温度、热状态、APK 哈希和生效配置逐批保存。

HTTP 阶段和 120 秒请求超时均从 benchmark 配额接纳后开始；配额等待单独记录，不消耗 HTTP 超时，引擎排队与完整响应读取仍包含在 HTTP 中。当前 OkHttp 同 host 异步调度默认阈值为 5；benchmark 观测到的并发 8 不能直接当作八个服务端在途请求。具体源码和计时边界见 [RESEARCH.md](RESEARCH.md)。Provider 输出 tokens 没有推理分解，HTTP 耗时也没有服务端推理/网络分解。

OpenMinis 的原生 loop 尚不能隔离为可运行候选，只报告集成障碍；上游 pi 仅有 Node 回放，尚无 Android JS 桥接。没有替它们编造性能成绩。
