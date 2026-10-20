"""Whole-batch summaries; exploratory tails; paired block bootstrap only for formal runs."""
import csv
import html
import json
import math
import random
import statistics
import hashlib
from pathlib import Path


def quantile(values, probability):
    values = sorted(v for v in values if v is not None)
    if not values:
        return None
    position = (len(values) - 1) * probability
    lo, hi = math.floor(position), math.ceil(position)
    return values[lo] + (values[hi] - values[lo]) * (position - lo)


def paired_ci(pairs, seed=42, samples=4000):
    """Resample independent time blocks, preserving paired whole batches within each block."""
    blocks = {}
    for period, baseline, candidate in pairs:
        if baseline <= 0 or candidate <= 0:
            raise ValueError("Invalid batch duration")
        blocks.setdefault(period, []).append((baseline - candidate) / baseline)
    if len(blocks) < 3:
        return None
    values = [statistics.mean(v) for v in blocks.values()]
    rng = random.Random(seed)
    draws = [statistics.mean(rng.choices(values, k=len(values))) for _ in range(samples)]
    return dict(mean=statistics.mean(values), lower95=quantile(draws, .025),
                upper95=quantile(draws, .975), blocks=len(blocks), pairs=len(pairs),
                method="paired-time-block-bootstrap-whole-batches")


def read_events(path):
    events = []
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines()):
        try:
            event = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"Corrupt trace line {index + 1}") from error
        if event.get("sequence") != index or not isinstance(event.get("elapsed_ms"), (int, float)):
            raise ValueError("Non-sequential trace")
        if events and event["elapsed_ms"] < events[-1]["elapsed_ms"]:
            raise ValueError("Non-monotonic trace")
        events.append(event)
    if not events or events[0]["type"] != "run_start":
        raise ValueError("Missing run metadata")
    return events


def summarize(spec, events):
    meta = dict(events[0], **{k: v for e in events if e["type"] == "environment" for k, v in e.items()
                            if k not in ("type", "sequence", "elapsed_ms")})
    summaries = []
    for profile in spec["profiles"]:
        stream = [e for e in events if e.get("profile") == profile["id"]]
        start = next((e for e in stream if e["type"] == "profile_start"), {})
        end = next((e for e in stream if e["type"] == "profile_end"), {})
        cases = [e for e in stream if e["type"] == "case_terminal"]
        ids = profile.get("caseIds") or [c["id"] for c in spec["cases"]]
        if len({e["case_id"] for e in cases}) != len(cases):
            raise ValueError("Duplicate terminal outcomes")
        counts = {status: sum(e["outcome"] == status for e in cases) for status in (
            "SUCCEEDED", "PARTIAL", "FAILED", "QUALITY_FAILED", "SOURCE_CHANGED", "SOURCE_ERROR",
            "STORAGE_ERROR", "RUNTIME_ERROR", "CANCELLED", "UNEXECUTED")}
        counts["UNEXECUTED"] += len(ids) - len(cases)
        usable = sum(e.get("usable", False) for e in cases)
        batch = end.get("batch_ms")
        completed = bool(end.get("completed")) and len(cases) == len(ids)
        eligible = completed and usable == len(ids) and all(e.get("audited") for e in cases)
        throughput = usable * 60000 / batch if batch else None
        phases = {name: sum(e.get(name, 0) or 0 for e in cases) for name in (
            "queue_ms", "http_ms", "parse_validate_ms", "storage_ms", "runtime_init_ms", "runtime_ms")}
        hashes = {e["case_id"]: e.get("request_hash") for e in cases}
        tokens = {name: sum(e[name] for e in cases if e.get(name) is not None)
                  if cases and all(e.get(name) is not None for e in cases if e.get("http_requests", 0)) else None
                  for name in ("prompt_tokens", "output_tokens", "cached_tokens")}
        summaries.append(dict(id=profile["id"], runtime=profile["runtime"], runtime_version=start.get("runtime_version"), concurrency=profile["concurrency"],
            period=profile.get("period", "pilot"), round=profile.get("round", 0), warmup=profile.get("warmup", False),
            load_model=start.get("load_model", "fixed_work"), expected=len(ids), cases=ids,
            started=bool(start), completed=completed, eligible=eligible, outcomes=counts, usable=usable,
            batch_ms=batch, usable_per_minute=throughput, first_usable_ms=end.get("first_usable_ms"),
            p50_ms=quantile([e.get("latency_ms") for e in cases if e.get("http_requests", 0)], .5),
            p95_ms=quantile([e.get("latency_ms") for e in cases if e.get("http_requests", 0)], .95),
            initialization_ms=start.get("initialization_ms"), phases_sum_ms=phases,
            http_requests=sum(e.get("http_requests", 0) for e in cases), tool_calls=start.get("tool_calls", 0),
            max_inflight_http=end.get("max_inflight_http"), max_active_tasks=end.get("max_active_tasks"),
            stop_reason=end.get("stop_reason"), heap_used_bytes=end.get("heap_used_bytes"),
            request_hashes=hashes, review_notices=sum(e.get("review_notices", 0) for e in cases), **tokens))
    recommendation = recommend(meta, summaries)
    used = sum(e["type"] == "http_start" for e in events)
    ceiling = spec.get("budgetLimit", 50)
    if used > spec["maxRequests"] or used + spec.get("previousRequests", 0) > ceiling:
        raise ValueError("Request budget exceeded")
    return dict(metadata=meta, finished=events[-1]["type"] == "run_end" and not events[-1].get("cancelled", False), requests_used=used,
        session_requests_used=used + spec.get("previousRequests", 0), authorized_limit=ceiling,
        request_limit=spec["maxRequests"], unused_budget=spec["maxRequests"] - used,
        profiles=summaries, recommendation=recommendation, estimates=estimate(summaries),
        limitations=["Pilot tails are descriptive; eight diaries cannot establish a reliable p95 ranking.",
            "Synchronous trace and private-answer logging is included; storage phase includes benchmark artifacts and Room.",
            "Phase sums overlap under concurrency and cannot be added to predict batch time.",
            "Heap is a snapshot, not peak memory; HTTP connection reuse is allowed, not proven.",
            "Checkpoint coverage is limited; manual omissions/hallucinations review remains necessary.",
            "Non-streaming TTFT is not applicable; first usable means persisted, fresh, minimally valid.",
            "JVM/Robolectric and Node results do not establish Android device performance."])


def recommend(meta, profiles):
    valid = [p for p in profiles if not p["warmup"] and p["load_model"] == "fixed_work" and p["eligible"]]
    fair = []
    if valid:
        reference = next((p for p in valid if p["runtime"] == "baseline"), valid[0])
        fair = [p for p in valid if p["cases"] == reference["cases"] and p["request_hashes"] == reference["request_hashes"]]
    curve = [p for p in fair if p["runtime"] == "lightweight"]
    planned_curve = [p for p in profiles if not p["warmup"] and p["runtime"] == "lightweight"]
    curve_complete = {p["concurrency"] for p in planned_curve} >= {1, 2, 4, 8} and all(p["completed"] for p in planned_curve)
    c = None
    if curve and curve_complete:
        qualifying = {n for n in {p["concurrency"] for p in curve}
                      if all(p in curve for p in planned_curve if p["concurrency"] == n)}
        throughput = {n: statistics.mean(p["usable_per_minute"] for p in curve if p["concurrency"] == n) for n in qualifying}
        if throughput:
            c = min(n for n, value in throughput.items() if value >= .95 * max(throughput.values()))
    intervals = {}
    complete_comparisons = set()
    for runtime in sorted({p["runtime"] for p in fair} - {"baseline", "lightweight"}):
        for concurrency in sorted({p["concurrency"] for p in fair if p["runtime"] == runtime}):
            pairs = []
            for candidate in fair:
                if candidate["runtime"] != runtime or candidate["concurrency"] != concurrency:
                    continue
                base = next((p for p in fair if p["runtime"] == "lightweight" and p["concurrency"] == concurrency and
                             (p["period"], p["round"]) == (candidate["period"], candidate["round"])), None)
                if base:
                    pairs.append((base["period"], base["batch_ms"], candidate["batch_ms"]))
            key = f"{runtime}-c{concurrency}-vs-lightweight-c{concurrency}"
            intervals[key] = paired_ci(pairs)
            planned = [p for p in profiles if not p["warmup"] and p["runtime"] == runtime and p["concurrency"] == concurrency]
            if planned and len(pairs) == len(planned):
                complete_comparisons.add(key)
    deployed = meta.get("platform") == "android-device" and meta.get("mode") == "live"
    winners = [key for key, ci in intervals.items() if ci and ci["lower95"] > .10 and deployed and key in complete_comparisons]
    return dict(exploratory_concurrency=c if meta.get("mode") == "live" else None,
        concurrency_curve_complete=curve_complete,
        runtime_candidates_passing_speed_gate=winners, paired_batch_intervals=intervals,
        runtime_comparison_reference="lightweight at the same concurrency, period and round",
        final_winner=None, default_runtime="direct-or-lightweight-kotlin",
        reason="Final selection also requires deployment and integration evidence; no winner from replay or one pilot period.")


def estimate(profiles, folder_size=173):
    cases = [p for p in profiles if not p["warmup"] and p["completed"] and p["http_requests"]]
    tokens = {}
    for name in ("prompt_tokens", "output_tokens"):
        ratios = [p[name] / p["http_requests"] for p in cases if p[name] is not None]
        tokens[name] = round(statistics.mean(ratios) * folder_size * 2 * 3) if ratios else None
    return dict(formal_design="Two shortlisted configurations, three paired whole-folder blocks; not authorized by pilot budget",
                folder_files=folder_size, configurations=2, paired_blocks=3, requests=folder_size * 2 * 3,
                projected_tokens=tokens, estimated_currency_cost=None,
                cost_reason="Provider rates must be supplied; no invented price or automatic extra calls.")


def write_report(directory, quality_audit=None):
    directory = Path(directory)
    spec = json.loads((directory / "spec.json").read_text(encoding="utf-8"))
    events = read_events(directory / "events.jsonl")
    result = summarize(spec, events)
    if quality_audit:
        audit = json.loads(Path(quality_audit).read_text(encoding="utf8"))
        if audit["parent_trace_sha256"] != hashlib.sha256((directory / "events.jsonl").read_bytes()).hexdigest():
            raise ValueError("Quality audit belongs to a different original trace")
        audited = {(c["profile"], c["case_id"]): c for c in audit["cases"]}
        corrected = []
        for e in events:
            current = dict(e)
            if e["type"] == "case_terminal" and (e.get("profile"), e.get("case_id")) in audited:
                c = audited[(e["profile"], e["case_id"])]
                answer = json.loads((directory / "private-answers" / f"{e['profile']}-{e['case_id']}.json").read_text(encoding="utf8"))["raw_model_json"]
                if c["response_sha256"] != hashlib.sha256(answer.encode()).hexdigest() or c["source_hash"] != e["source_hash"]:
                    raise ValueError("Audit response or source identity mismatch")
                current.update({k: c[k] for k in ("outcome", "usable", "audited", "checkpoint_failures")})
            corrected.append(current)
        result = summarize(spec, corrected)
        result["quality_audit"] = dict(path=str(Path(quality_audit).resolve()), method=audit["method"],
            parent_trace_sha256=audit["parent_trace_sha256"], regraded=len(audited))
        result["limitations"].append("Quality was regraded offline; live request events, answers and durations are unchanged.")
        for p in result["profiles"]:
            before = next((e["case_id"] for e in events if e.get("profile") == p["id"] and e["type"] == "case_terminal" and e.get("usable")), None)
            after = next((e["case_id"] for e in corrected if e.get("profile") == p["id"] and e["type"] == "case_terminal" and e.get("usable")), None)
            if before != after:
                p["first_usable_original_ms"] = p["first_usable_ms"]
                p["first_usable_ms"] = None  # Do not fabricate an unrecorded boundary after regrading.
    (directory / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    columns = ["id", "runtime", "concurrency", "expected", "completed", "usable", "batch_ms", "usable_per_minute",
               "first_usable_ms", "p50_ms", "p95_ms", "http_requests", "max_inflight_http", "max_active_tasks",
               "prompt_tokens", "output_tokens", "cached_tokens", "stop_reason"]
    with (directory / "summary.csv").open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(result["profiles"])
    # No diary text or responses in HTML; all interpolated values are escaped.
    rows = "".join("<tr>" + "".join(f"<td>{html.escape(str(p.get(k)))}</td>" for k in columns[:10]) + "</tr>"
                   for p in result["profiles"])
    payload = json.dumps(result, ensure_ascii=False).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    page = '''<!doctype html><html lang="zh"><meta charset="utf-8"><title>FitLog analysis benchmark</title>
<style>body{font:15px system-ui;max-width:1180px;margin:40px auto;padding:0 20px;color:#162c35;background:#f6faf9}
table{border-collapse:collapse;width:100%;background:white}td,th{padding:9px;text-align:left;border-bottom:1px solid #ddd}
.scroll{overflow:auto}svg{background:white;width:100%;max-width:900px;border-radius:12px}select{padding:8px}pre{white-space:pre-wrap}</style>
<h1>FitLog analysis benchmark</h1><p id="scope"></p><p>曲线仅展示完整批次；部分批次保留在表格中。固定工作量与固定到达率分别分析；离线速度不证明真实模型或 Android 表现。</p><p id="audit"></p>
<label>曲线指标 <select id="metric"><option value="usable_per_minute">可核对候选 / 分钟</option><option value="batch_ms">批次完成时间 ms</option>
<option value="first_usable_ms">首个可核对候选 ms</option><option value="p95_ms">探索性 p95 ms</option></select></label>
<svg id="chart" viewBox="0 0 900 330" role="img" aria-label="并发和批次性能曲线"></svg>
<div class="scroll"><table><thead><tr>HEADERS</tr></thead><tbody>ROWS</tbody></table></div>
<h2>选型证据</h2><pre id="decision"></pre><h2>预算估算</h2><pre id="estimate"></pre><h2>证据限制</h2><ul id="limits"></ul>
<script type="application/json" id="data">PAYLOAD</script><script>
const data=JSON.parse(document.getElementById('data').textContent);
document.getElementById('scope').textContent=`${data.metadata.mode} / ${data.metadata.platform} · ${data.requests_used}/${data.request_limit} requests`;
if(data.quality_audit)document.getElementById('audit').textContent='质量已使用保存的响应离线复核；原始请求记录和计时未改变。';
document.getElementById('decision').textContent=JSON.stringify(data.recommendation,null,2);
document.getElementById('estimate').textContent=JSON.stringify(data.estimates,null,2);
for(const x of data.limitations){const li=document.createElement('li');li.textContent=x;document.getElementById('limits').append(li)}
function draw(){const key=document.getElementById('metric').value,svg=document.getElementById('chart');svg.replaceChildren();
const points=data.profiles.filter(p=>!p.warmup&&p.completed&&p.load_model==='fixed_work'&&p[key]!=null),max=Math.max(1,...points.map(p=>p[key]));
function add(tag,attrs,text){const e=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const [k,v] of Object.entries(attrs))e.setAttribute(k,v);if(text)e.textContent=text;svg.append(e);return e}
add('path',{d:'M70 20 V270 H850',fill:'none',stroke:'#789'});
for(const c of [1,2,4,8,16])add('text',{x:70+(c-1)*48,y:298},String(c));add('text',{x:360,y:324},'在途 HTTP 并发上限');
add('text',{x:5,y:25},max.toFixed(1));add('text',{x:35,y:272},'0');
for(const p of points){const x=70+(p.concurrency-1)*48,y=270-p[key]/max*240;
const dot=add('circle',{cx:x,cy:y,r:6,fill:p.runtime==='adk'?'#9b4d99':p.runtime==='baseline'?'#d87920':'#00836d'});
const title=document.createElementNS('http://www.w3.org/2000/svg','title');title.textContent=`${p.id}: ${p[key].toFixed(2)}; eligible=${p.eligible}`;dot.append(title);
add('text',{x:x+9,y:y-8,'font-size':12},p.id)} }
document.getElementById('metric').addEventListener('change',draw);draw();</script></html>'''
    page = page.replace("HEADERS", "".join(f"<th>{html.escape(k)}</th>" for k in columns[:10])).replace("ROWS", rows).replace("PAYLOAD", payload)
    (directory / "report.html").write_text(page, encoding="utf-8")
    return result


def compare_reports(directories, output):
    """Combine independent periods only when all workload and transport controls match."""
    results = [write_report(directory) for directory in directories]
    controls = ("mode", "platform", "dataset_fingerprint", "model", "endpoint", "prompt_sha256", "validator_version",
                "connect_timeout_ms", "request_timeout_ms", "code_fingerprint", "proxy_url", "study_stage")
    reference = results[0]["metadata"]
    if any(reference.get(k) in (None, "unspecified") for k in ("prompt_sha256", "code_fingerprint", "dataset_fingerprint")):
        raise ValueError("Formal comparison requires known code, prompt and dataset fingerprints")
    for result in results[1:]:
        if any(result["metadata"].get(k) != reference.get(k) for k in controls):
            raise ValueError("Comparison controls differ; cannot create paired confidence intervals")
    profiles = [p for result in results for p in result["profiles"]]
    identities = [(p["runtime"], p["concurrency"], p["period"], p["round"]) for p in profiles if p["started"]]
    if len(set(identities)) != len(identities):
        raise ValueError("Repeated period/round labels; independent measurement periods must be explicit")
    result = dict(metadata=reference, runs=[str(Path(p).resolve()) for p in directories],
                  recommendation=recommend(reference, profiles))
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / "comparison.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf8")
    return result
