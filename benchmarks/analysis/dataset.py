"""Read-only dataset freezing; hashes match FitLog ContentTextSnapshot."""
import hashlib
import json
import statistics
import uuid
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized(text):
    return text.removeprefix("\ufeff").replace("\r\n", "\n").replace("\r", "\n")


def case_from_bytes(rel_path, raw, **extras):
    text = normalized(raw.decode("utf-8"))
    return dict(id=digest(rel_path.encode())[:16], relPath=rel_path, text=text,
                rawSha256=digest(raw), contentHash=digest(text.encode()), rawBytes=len(raw),
                charsUtf16=len(text.encode("utf-16-le")) // 2, **extras)


def fingerprint(cases):
    identities = [{k: c[k] for k in ("id", "relPath", "rawSha256", "contentHash")} for c in cases]
    return digest(json.dumps(identities, sort_keys=True, ensure_ascii=False).encode())


def inventory(root):
    root = Path(root).resolve(strict=True)
    if not root.is_dir():
        raise ValueError("Dataset root must be a directory")
    cases = []
    for file in sorted(root.rglob("*")):
        relative = file.relative_to(root)
        if any(p.startswith(".") for p in relative.parts) or file.suffix.lower() != ".md":
            continue
        # A link can escape the authorized source root; don't silently include it.
        if file.is_symlink() or not file.resolve().is_relative_to(root):
            raise ValueError("Dataset contains a linked source")
        if file.is_file():
            cases.append(case_from_bytes(relative.as_posix(), file.read_bytes()))
    if not cases:
        raise ValueError("No visible Markdown files found")
    lengths = [len(c["text"].encode("utf-16-le")) // 2 for c in cases]
    return dict(sourceRoot=str(root), datasetFingerprint=fingerprint(cases), vaultId=str(uuid.uuid4()),
                cases=cases, inventory=dict(files=len(cases), empty=sum(not c["text"] for c in cases),
                chars_utf16=sum(lengths), median_chars=statistics.median(lengths), max_chars=max(lengths),
                bytes=sum(c["rawBytes"] for c in cases)))


def select_pilot(cases):
    """Hints stratify sampling only; they never exclude files from the workload."""
    import re
    ordered = sorted(cases, key=lambda c: (len(c["text"]), c["relPath"]))
    hints = re.compile(r"kg|公斤|lb|卧推|引体|深蹲|硬拉|划船|跑步|[×✖]", re.I)
    pools = [ordered, [c for c in ordered if not hints.search(c["text"])],
             [c for c in ordered if hints.search(c["text"])],
             [c for c in ordered if hints.search(c["text"]) and re.search(r"[➕+✖×]", c["text"])]]
    chosen = []
    for pool in pools:
        for case in (pool[:1] + pool[-1:]):
            if case["id"] not in {c["id"] for c in chosen}:
                chosen.append(case)
    for q in (0.25, 0.5, 0.75, 0.125, 0.875):
        case = ordered[min(len(ordered)-1, int((len(ordered)-1)*q))]
        if case["id"] not in {c["id"] for c in chosen}:
            chosen.append(case)
    for case in ordered:
        if case["id"] not in {c["id"] for c in chosen}:
            chosen.append(case)
        if len(chosen) >= 8:
            break
    if len(chosen) < 8:
        raise ValueError("Pilot needs eight distinct source files")
    return chosen[:8]


def verify_frozen(dataset):
    if dataset["datasetFingerprint"] != fingerprint(dataset["cases"]):
        raise ValueError("Dataset identity manifest changed")
    if not dataset.get("sourceRoot"):
        return
    root = Path(dataset["sourceRoot"]).resolve(strict=True)
    for c in dataset["cases"]:
        file = (root / c["relPath"]).resolve(strict=True)
        if not file.is_relative_to(root) or digest(file.read_bytes()) != c["rawSha256"]:
            raise ValueError("Frozen source changed; create a new inventory explicitly")


def replay_dataset(repo):
    resource = repo / "benchmarks/analysis/fixtures"
    pairs = [("user-sample", "expected"), ("constructed-boundaries", "expected"), ("device-2026-09-29", "response")]
    cases = []
    for index in range(8):
        stem, kind = pairs[index % len(pairs)]
        raw = (resource / f"{stem}.md").read_bytes()
        response = (resource / f"{stem}.{kind}.json").read_text(encoding="utf-8-sig")
        candidate = json.loads(response)
        points = []
        for s, session in enumerate(candidate["sessions"]):
            for e, exercise in enumerate(session["exercises"]):
                for key in ("rawName",):
                    points.append(dict(path=f"$.sessions[{s}].exercises[{e}].{key}", expectedJson=json.dumps(exercise[key], ensure_ascii=False)))
        cases.append(case_from_bytes(f"2026-09-{index+20:02}.md", raw, responseJson=response,
                                    delayMs=40 + index*3, checkpoints=points, audited=True))
    return dict(datasetFingerprint=fingerprint(cases), vaultId=str(uuid.uuid4()), cases=cases)
