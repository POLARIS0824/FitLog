"""FitLog benchmark CLI. Offline by default; live is bounded and explicit."""
import argparse
import getpass
import json
import os
import random
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import urlsplit

from dataset import inventory, select_pilot, verify_frozen, replay_dataset, fingerprint
from reporting import write_report, compare_reports, read_events
from dataset import digest

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def revision():
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()


def base_url(value):
    value = value.strip().rstrip("/")
    if value.endswith("/chat/completions"):
        value = value[:-len("/chat/completions")]
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Base URL must be HTTPS without credentials, query or fragment")
    return value


def profile(identifier, runtime, concurrency, ids, **kwargs):
    return dict(id=identifier, runtime=runtime, concurrency=concurrency, caseIds=ids, **kwargs)


def study_profiles(stage, ids, period, start_round=0, rounds=1, concurrency=2, configs=None, seed=42):
    """Randomize complete batches within a period; repeated rounds are NOT independent periods."""
    if stage == "connectivity":
        return [profile("connectivity", "lightweight", 1, ids[:2], period=period, round=start_round, warmup=True)]
    choices = [("lightweight", c) for c in (1, 2, 4, 8)] if stage == "concurrency" else [
        ("lightweight", concurrency), ("adk", concurrency)]
    if stage == "folder":
        if not configs or not 1 <= len(configs) <= 2:
            raise ValueError("Folder validation requires one or two explicit --configs runtime:concurrency")
        choices = []
        for value in configs:
            runtime, count = value.split(":")
            count = int(count)
            if runtime not in ("baseline", "lightweight", "adk") or not 1 <= count <= 16 or (runtime == "baseline" and count != 1):
                raise ValueError("Invalid folder configuration")
            choices.append((runtime, count))
        if len(set(choices)) != len(choices):
            raise ValueError("Duplicate folder configurations")
    if not 1 <= rounds <= 10 or not 1 <= concurrency <= 16:
        raise ValueError("Invalid round count or concurrency")
    rng = random.Random(seed)
    result = []
    for round_index in range(start_round, start_round + rounds):
        batches = [profile(f"{runtime}-c{count}-r{round_index}", runtime, count, ids,
                           period=period, round=round_index) for runtime, count in choices]
        rng.shuffle(batches)
        result.extend(batches)
    return result


def make_spec(dataset, output, mode, maximum=50, base="https://benchmark.invalid/v1", model="replay", key_env="FITLOG_BENCH_API_KEY"):
    files = [f for root in (REPO / "app/src/main", REPO / "app/src/benchmark", HERE) for f in root.rglob("*")
             if f.is_file() and f.suffix in (".kt", ".py", ".mjs", ".kts", ".xml", ".pro") and
             not any(part in ("out", "node_modules", "__pycache__") for part in f.relative_to(REPO).parts)]
    files += [REPO / "app/build.gradle.kts", REPO / "app/benchmark-rules.pro", REPO / "app/proguard-rules.pro",
              REPO / "gradle/libs.versions.toml", HERE / "pi/package-lock.json"]
    code_hash = digest("\n".join(f"{f.relative_to(REPO).as_posix()}:{digest(f.read_bytes())}" for f in sorted(set(files)) if f.exists()).encode())
    return dict(mode=mode, outputDir=str(output.resolve()), datasetFingerprint=dataset["datasetFingerprint"],
                vaultId=dataset["vaultId"], sourceRoot=dataset.get("sourceRoot"), sourceRevision=revision(),
                baseUrl=base_url(base), modelId=model, apiKeyEnv=key_env, maxRequests=maximum,
                seed=42, codeFingerprint=code_hash, cases=dataset["cases"], profiles=[])


def execute(spec, output, key=None):
    if (output / "events.jsonl").exists() or (output / "spec.json").exists():
        raise ValueError("Run directory already used; traces cannot be overwritten or resumed")
    dump(output / "spec.json", spec)
    environment = os.environ.copy()
    environment["FITLOG_BENCH_SPEC"] = str((output / "spec.json").resolve())
    if spec.get("proxyUrl"):
        proxy = urlsplit(spec["proxyUrl"])
        environment["FITLOG_BENCH_PROXY_HOST"] = proxy.hostname
        environment["FITLOG_BENCH_PROXY_PORT"] = str(proxy.port)
    if key:
        environment[spec["apiKeyEnv"]] = key
    args = [str(REPO / "gradlew.bat"), "--no-daemon", ":app:testBenchmarkUnitTest", "--tests",
            "com.example.fitlog.benchmark.BenchmarkHarnessTest"]
    # The credential travels in child environment only, never in argv or JSON.
    result = subprocess.run(args, cwd=REPO, env=environment)
    if (output / "events.jsonl").exists():
        write_report(output)
    if result.returncode:
        raise RuntimeError("Native harness failed; see Gradle output")
    print(f"Report: {output / 'report.html'}")


def new_output(kind):
    return HERE / "out" / f"{kind}-{time.time_ns()}"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command")
    inv = sub.add_parser("inventory")
    inv.add_argument("--source", type=Path, default=Path(r"D:\Obsidian\POLARIS\dairy"))
    inv.add_argument("--output", type=Path, default=HERE / "out/inventory")
    replay = sub.add_parser("replay")
    replay.add_argument("--output", type=Path)
    replay.add_argument("--device-spec", action="store_true")
    replay.add_argument("--audit-run", type=Path, help="Offline regrade of captured answers; never invokes a provider")
    replay.add_argument("--checkpoints", type=Path, help="Revised frozen pilot with semantic checkpoints")
    live = sub.add_parser("live")
    live.add_argument("--dataset", type=Path, required=True)
    live.add_argument("--base-url", required=True)
    live.add_argument("--model", required=True)
    live.add_argument("--key-env", default="FITLOG_BENCH_API_KEY")
    live.add_argument("--key-file", type=Path, help="Read a local credential file; contents never enter argv or reports")
    live.add_argument("--key-stdin", action="store_true", help="Read one line without echo; key is never persisted")
    live.add_argument("--output", type=Path)
    live.add_argument("--max-requests", type=int, help="Hard attempt limit, including failures; defaults to planned count outside pilot")
    live.add_argument("--budget-limit", type=int, default=50, help="Explicit study ceiling; raising the original 50 requires prior user authorization")
    live.add_argument("--stage", choices=("pilot", "connectivity", "concurrency", "runtime", "folder"), default="pilot")
    live.add_argument("--rounds", type=int, default=1, help="Repeats within this period; does not create independent time periods")
    live.add_argument("--concurrency", type=int, default=2, help="Shared concurrency for the runtime comparison")
    live.add_argument("--configs", nargs="+", help="Explicit shortlisted runtime:concurrency configurations for folder validation")
    live.add_argument("--prepare-only", action="store_true", help="Freeze plan.json without resolving credentials or making API requests")
    live.add_argument("--device", action="store_true", help="Run the same-APK native Android instrumentation")
    live.add_argument("--device-serial")
    live.add_argument("--adb", type=Path)
    live.add_argument("--proxy", help="Explicit HTTP proxy for JVM transport; no credentials allowed")
    live.add_argument("--period", default="pilot", help="Independent measurement period label")
    live.add_argument("--round", type=int, default=0)
    live.add_argument("--order-seed", type=int, default=42, help="Reproducible measured-profile order; vary across paired periods")
    live.add_argument("--previous-run", type=Path, help="Explicit manual restart: count predecessor requests against the same 50-call authorization")
    live.add_argument("--profiles", nargs="+", choices=("baseline-c1", "lightweight-c2", "adk-c4", "lightweight-c4", "lightweight-c8"),
                      help="Explicitly run only still-unexecuted pilot configurations; never retries a failed profile automatically")
    report = sub.add_parser("report")
    report.add_argument("run", type=Path, nargs="+")
    report.add_argument("--output", type=Path, help="Required when comparing multiple independent run directories")
    report.add_argument("--quality-audit", type=Path)
    options = parser.parse_args(argv if argv is not None else (sys.argv[1:] or ["replay"]))
    if options.command == "inventory":
        dataset = inventory(options.source)
        dump(options.output / "dataset.json", dataset)
        dump(options.output / "pilot.json", dict(dataset, cases=select_pilot(dataset["cases"]),
            datasetFingerprint=fingerprint(select_pilot(dataset["cases"]))))
        print(json.dumps(dataset["inventory"]))
        print(f"Frozen dataset: {options.output / 'dataset.json'}")
    elif options.command == "replay":
        output = options.output or new_output("replay")
        if options.audit_run:
            if not options.checkpoints or options.device_spec:
                raise ValueError("Offline audit requires --checkpoints and cannot be a device spec")
            audit_replay(options.audit_run, options.checkpoints, output)
            return
        dataset = replay_dataset(REPO)
        spec = make_spec(dataset, output, "replay", maximum=50)
        ids = [c["id"] for c in dataset["cases"]]
        spec["profiles"] = [profile("baseline-c1", "baseline", 1, ids)] + [
            profile(f"lightweight-c{c}", "lightweight", c, ids) for c in (1, 2, 4, 8)] + [
            profile("adk-c4", "adk", 4, ids)]
        # 48 sends; failure injection and arrival/cancel scenarios are separate specs to preserve budget accounting.
        if options.device_spec:
            dump(output / "device-spec.json", spec)
            print(f"Offline device spec: {output / 'device-spec.json'}")
        else:
            execute(spec, output)
    elif options.command == "live":
        if not 1 <= options.budget_limit <= 10000:
            raise ValueError("Study budget must be between 1 and 10000")
        dataset = json.loads(options.dataset.read_text(encoding="utf-8"))
        verify_frozen(dataset)
        if options.stage != "folder" and len(dataset["cases"]) != 8:
            raise ValueError("Pilot dataset must contain exactly eight files")
        if any(c.get("fault") or c.get("httpStatus", 200) != 200 for c in dataset["cases"]):
            raise ValueError("Live datasets cannot contain injected faults")
        if options.stage in ("concurrency", "runtime") and not all(c.get("audited") and c.get("checkpoints") for c in dataset["cases"]):
            raise ValueError("Selection experiments require audited fact checkpoints for every pilot diary")
        output = options.output or new_output("live")
        spec = make_spec(dataset, output, "live", options.max_requests or 50, options.base_url, options.model, options.key_env)
        spec.update(budgetLimit=options.budget_limit, studyStage=options.stage)
        if options.device:
            spec["platform"] = "android-device"
        prior_warmups = 0
        if options.previous_run:
            prior = json.loads((options.previous_run / "spec.json").read_text(encoding="utf8"))
            controls = ("baseUrl", "modelId") + (("datasetFingerprint",) if options.budget_limit == 50 else ())
            if any(prior[k] != spec[k] for k in controls):
                raise ValueError("Predecessor must use the same endpoint/model and the original pilot must retain its dataset")
            events = read_events(options.previous_run / "events.jsonl")
            spec["previousRequests"] = prior.get("previousRequests", 0) + sum(e["type"] == "http_start" for e in events)
            warmups = {p["id"] for p in prior["profiles"] if p.get("warmup")}
            prior_warmups = prior.get("previousWarmups", 0) + sum(e["type"] == "http_start" and e.get("profile") in warmups for e in events)
            spec["previousWarmups"] = prior_warmups
        if options.proxy:
            proxy = urlsplit(options.proxy)
            if proxy.scheme != "http" or not proxy.hostname or not proxy.port or proxy.username or proxy.password or proxy.path not in ("", "/") or proxy.query or proxy.fragment:
                raise ValueError("Proxy must be http://host:port without credentials or extra URL parts")
            spec["proxyUrl"] = options.proxy
        ids = [c["id"] for c in dataset["cases"]]
        spec["profiles"] = ([profile("warmup", "baseline", 1, ids[prior_warmups:2], warmup=True)] if prior_warmups < 2 else []) + [
            profile("baseline-c1", "baseline", 1, ids), profile("lightweight-c2", "lightweight", 2, ids),
            profile("adk-c4", "adk", 4, ids), profile("lightweight-c4", "lightweight", 4, ids),
            profile("lightweight-c8", "lightweight", 8, ids)]
        for config in spec["profiles"]:
            config.update(period=options.period, round=options.round)
        if options.profiles:
            spec["profiles"] = [p for p in spec["profiles"] if p["id"] in options.profiles]
        warmups = [p for p in spec["profiles"] if p.get("warmup")]
        measured = [p for p in spec["profiles"] if not p.get("warmup")]
        random.Random(options.order_seed).shuffle(measured)
        spec.update(seed=options.order_seed, profiles=warmups + measured)
        if options.stage != "pilot":
            if options.profiles:
                raise ValueError("--profiles applies to the original pilot only; use --stage and --configs")
            spec["profiles"] = study_profiles(options.stage, ids, options.period, options.round,
                options.rounds, options.concurrency, options.configs, options.order_seed)
        planned = sum(len(p["caseIds"]) for p in spec["profiles"])
        spec["maxRequests"] = options.max_requests if options.max_requests is not None else (50 if options.stage == "pilot" else planned)
        if not 1 <= spec["maxRequests"] <= options.budget_limit or spec.get("previousRequests", 0) + spec["maxRequests"] > options.budget_limit:
            raise ValueError("Predecessor plus new request limit exceeds the explicit study budget")
        if options.device and options.proxy:
            raise ValueError("Native device experiments use the phone network; JVM proxy cannot be reused")
        if options.prepare_only:
            if (output / "plan.json").exists() or (output / "events.jsonl").exists():
                raise ValueError("Plan directory already used")
            dump(output / "plan.json", spec)
            print(f"Prepared {options.stage}: {planned} planned attempts, ceiling {spec['maxRequests']}; no API calls")
            print(output / "plan.json")
            return
        if options.key_file and options.key_stdin:
            raise ValueError("Select one credential input")
        if options.key_file:
            key = options.key_file.read_text(encoding="utf-8-sig").strip()
        else:
            key = (getpass.getpass("API key (not saved): ") if sys.stdin.isatty() else sys.stdin.readline().rstrip("\r\n")) if options.key_stdin else os.environ.get(options.key_env)
        if not key:
            if not sys.stdin.isatty():
                raise ValueError("Missing credential; use --key-file, --key-env or --key-stdin")
            key = getpass.getpass("API key (not saved): ")
        if not key or any(not 33 <= ord(c) <= 126 for c in key):
            raise ValueError("Missing or invalid credential")
        if options.device:
            from device import execute_device
            execute_device(REPO, spec, output, key, options.adb, options.device_serial)
        else:
            execute(spec, output, key)
        verify_frozen(dataset)
    elif options.command == "report":
        if len(options.run) == 1:
            write_report(options.run[0], options.quality_audit)
            print(options.run[0] / "report.html")
        else:
            if options.quality_audit:
                raise ValueError("A quality audit belongs to exactly one original run")
            if not options.output:
                raise ValueError("Multi-run comparison requires --output")
            compare_reports(options.run, options.output)
            print(options.output / "comparison.json")
    else:
        parser.print_help()


def audit_replay(parent, checkpoints, output):
    """Same production parser/validator, captured answers, independent Room; timing never replaces live timing."""
    original = json.loads((parent / "spec.json").read_text(encoding="utf8"))
    revised = json.loads(checkpoints.read_text(encoding="utf8"))
    if original["mode"] != "live" or original["datasetFingerprint"] != revised["datasetFingerprint"]:
        raise ValueError("Audit checkpoints must describe the same frozen live pilot")
    verify_frozen(revised)
    by_id = {c["id"]: c for c in original["cases"]}
    revised_by_id = {c["id"]: c for c in revised["cases"]}
    events = read_events(parent / "events.jsonl")
    cases, mapping, profiles = [], {}, []
    for p in original["profiles"]:
        ids = []
        for event in events:
            if event.get("profile") != p["id"] or event["type"] != "case_terminal" or event["outcome"] not in ("SUCCEEDED", "QUALITY_FAILED", "PARTIAL"):
                continue
            c = by_id[event["case_id"]]
            artifact = json.loads((parent / "private-answers" / f"{p['id']}-{c['id']}.json").read_text(encoding="utf8"))
            if artifact["source_hash"] != c["contentHash"]:
                raise ValueError("Captured answer source identity mismatch")
            identifier = f"{p['id']}--{c['id']}"
            answer = artifact["raw_model_json"]
            cases.append(dict(c, id=identifier, responseJson=answer, delayMs=0,
                              checkpoints=revised_by_id[c["id"]]["checkpoints"], audited=True))
            ids.append(identifier)
            mapping[identifier] = dict(profile=p["id"], case_id=c["id"], response_sha256=digest(answer.encode()), source_hash=c["contentHash"])
        if ids:
            profiles.append(profile(p["id"], "lightweight", 1, ids, warmup=p.get("warmup", False)))
    if not cases or len(cases) > 50:
        raise ValueError("Audit requires 1..50 captured candidate responses")
    dataset = dict(cases=cases, datasetFingerprint=fingerprint(cases), vaultId=original["vaultId"])
    spec = make_spec(dataset, output, "replay", base=original["baseUrl"], model=original["modelId"])
    spec.update(profiles=profiles, platform="offline-quality-audit")
    provenance = dict(parent_trace_sha256=digest((parent / "events.jsonl").read_bytes()), mappings=mapping)
    dump(output / "audit-map.json", provenance)
    execute(spec, output)
    audited = read_events(output / "events.jsonl")
    if audited[0]["validator_version"] != events[0]["validator_version"]:
        raise ValueError("Validator changed; cannot regrade this measurement")
    provenance["cases"] = [dict(mapping[e["case_id"]], outcome=e["outcome"], usable=e["usable"],
        audited=e["audited"], checkpoint_failures=e["checkpoint_failures"]) for e in audited if e["type"] == "case_terminal"]
    provenance["method"] = "captured-response replay through unchanged FitLog parser and validator; no new live calls"
    dump(output / "quality-audit.json", provenance)
    write_report(parent, output / "quality-audit.json")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
