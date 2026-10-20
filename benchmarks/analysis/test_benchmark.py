import json
import hashlib
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path
import subprocess

from benchmark import base_url, main, study_profiles, REPO
from dataset import inventory, select_pilot, verify_frozen, normalized, replay_dataset
from reporting import paired_ci, recommend, summarize, read_events, write_report


class DatasetTests(unittest.TestCase):
    def test_inventory_preserves_originals_and_includes_empty_nontraining_nested_files(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            raw = b"\xef\xbb\xbfhello\r\nworld\r"
            (root / "original.md").write_bytes(raw)
            (root / "empty.MD").write_bytes(b"")
            (root / "nested").mkdir()
            (root / "nested/plain.md").write_text("ordinary", encoding="utf8")
            (root / ".obsidian").mkdir()
            (root / ".obsidian/hidden.md").write_text("hidden")
            data = inventory(root)
            self.assertEqual(data["inventory"]["files"], 3)
            self.assertEqual(data["inventory"]["empty"], 1)
            self.assertEqual((root / "original.md").read_bytes(), raw)
            source = next(c for c in data["cases"] if c["relPath"] == "original.md")
            self.assertEqual(source["text"], "hello\nworld\n")
            self.assertNotEqual(source["rawSha256"], source["contentHash"])
            verify_frozen(data)
            (root / "original.md").write_bytes(raw + b"changed")
            with self.assertRaises(ValueError):
                verify_frozen(data)

    def test_sampling_is_fixed_distinct_and_does_not_prefilter_workload(self):
        cases = [dict(id=str(i), relPath=f"{i}.md", text="ordinary" * i) for i in range(20)]
        cases[5]["text"] = "38kg 4×8"
        self.assertEqual(select_pilot(cases), select_pilot(cases))
        self.assertEqual(len(select_pilot(cases)), 8)
        self.assertEqual(len({c["id"] for c in select_pilot(cases)}), 8)
        self.assertEqual(len(cases), 20)
        self.assertIn(cases[0], select_pilot(cases))

    def test_endpoint_prefix_and_credentials_validation(self):
        self.assertEqual(base_url("https://api.ppio.com/openai/v1/chat/completions"), "https://api.ppio.com/openai/v1")
        for bad in ("http://host/v1", "https://key@host/v1", "https://host/?key=secret", "https://host/#secret"):
            with self.assertRaises(ValueError):
                base_url(bad)


class ReportTests(unittest.TestCase):
    def fixture(self):
        spec = dict(maxRequests=2, cases=[dict(id="a"), dict(id="b")], profiles=[dict(id="p", runtime="lightweight", concurrency=2)])
        events = [dict(type="run_start", mode="live", platform="jvm-robolectric"),
            dict(type="profile_start", profile="p"), dict(type="http_start", profile="p"),
            dict(type="case_terminal", profile="p", case_id="a", outcome="SUCCEEDED", usable=True, audited=True,
                 latency_ms=100, http_requests=1, request_hash="a"),
            dict(type="profile_end", profile="p", batch_ms=120, completed=False), dict(type="run_end")]
        for i, e in enumerate(events):
            e.update(sequence=i, elapsed_ms=i)
        return spec, events

    def test_incomplete_batch_is_reported_but_never_eligible(self):
        spec, events = self.fixture()
        result = summarize(spec, events)
        self.assertFalse(result["profiles"][0]["eligible"])
        self.assertEqual(result["profiles"][0]["outcomes"]["UNEXECUTED"], 1)
        self.assertIsNone(result["recommendation"]["exploratory_concurrency"])
        self.assertIsNone(result["recommendation"]["final_winner"])
        self.assertEqual(result["requests_used"], 1)

    def test_one_period_with_many_diaries_cannot_fabricate_confidence(self):
        self.assertIsNone(paired_ci([("one-period", 100, 70)] * 100))
        ci = paired_ci([("a", 100, 80), ("b", 110, 90), ("c", 90, 70)])
        self.assertLess(ci["lower95"], ci["upper95"])
        self.assertEqual(ci["blocks"], 3)
        self.assertEqual(ci, paired_ci([("a", 100, 80), ("b", 110, 90), ("c", 90, 70)]))

    def test_trace_rejects_corruption_and_report_escapes_labels(self):
        spec, events = self.fixture()
        spec["profiles"][0]["id"] = "<script>alert(1)</script>"
        for e in events:
            if "profile" in e:
                e["profile"] = spec["profiles"][0]["id"]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            (path / "spec.json").write_text(json.dumps(spec), encoding="utf8")
            (path / "events.jsonl").write_text("\n".join(json.dumps(e) for e in events), encoding="utf8")
            write_report(path)
            page = (path / "report.html").read_text(encoding="utf8")
            self.assertNotIn("<script>alert(1)</script>", page)
            self.assertIn("&lt;script&gt;", page)
            events[2]["sequence"] = 200
            (path / "events.jsonl").write_text("\n".join(json.dumps(e) for e in events), encoding="utf8")
            with self.assertRaises(ValueError):
                read_events(path / "events.jsonl")

    def test_budget_violation_fails_report(self):
        spec, events = self.fixture()
        spec["maxRequests"] = 0
        with self.assertRaises(ValueError):
            summarize(spec, events)

    def test_replay_and_mismatched_requests_cannot_produce_selection(self):
        p = dict(id="b", runtime="baseline", concurrency=1, eligible=True, completed=True, warmup=False, load_model="fixed_work",
            cases=["a"], request_hashes={"a": "x"}, period="pilot", round=0, batch_ms=100, usable_per_minute=600)
        c = dict(p, id="c", runtime="lightweight", concurrency=2, batch_ms=60, usable_per_minute=1000,
                 request_hashes={"a": "DIFFERENT-PROMPT"})
        decision = recommend(dict(mode="live", platform="android-device"), [p, c])
        self.assertIsNone(decision["exploratory_concurrency"])
        c["request_hashes"] = p["request_hashes"]
        decision = recommend(dict(mode="replay", platform="android-device"), [p, c])
        self.assertIsNone(decision["exploratory_concurrency"])
        self.assertFalse(decision["runtime_candidates_passing_speed_gate"])

    def test_runtime_speed_gate_never_confuses_concurrency_with_framework_speed(self):
        profiles = []
        for period in ("morning", "afternoon", "evening"):
            base = dict(id=period, runtime="baseline", concurrency=1, eligible=True, completed=True,
                        warmup=False, load_model="fixed_work", cases=["a"], request_hashes={"a": "hash"},
                        period=period, round=0, batch_ms=100, usable_per_minute=600)
            profiles += [base, dict(base, runtime="lightweight", concurrency=4, batch_ms=40),
                         dict(base, runtime="adk", concurrency=4, batch_ms=45)]
        decision = recommend(dict(mode="live", platform="android-device"), profiles)
        self.assertFalse(decision["runtime_candidates_passing_speed_gate"])
        self.assertLess(decision["paired_batch_intervals"]["adk-c4-vs-lightweight-c4"]["upper95"], 0)
        for p in profiles:
            if p["runtime"] == "adk":
                p["batch_ms"] = 30
        self.assertEqual(recommend(dict(mode="live", platform="android-device"), profiles)[
            "runtime_candidates_passing_speed_gate"], ["adk-c4-vs-lightweight-c4"])
        profiles.append(dict(profiles[-1], id="failed", period="failed", eligible=False, completed=False))
        self.assertFalse(recommend(dict(mode="live", platform="android-device"), profiles)["runtime_candidates_passing_speed_gate"])

    def test_explicit_extended_budget_is_reported_and_cancellation_is_not_completion(self):
        spec, events = self.fixture()
        spec.update(budgetLimit=1500, previousRequests=26)
        self.assertEqual(summarize(spec, events)["authorized_limit"], 1500)
        events[-1]["cancelled"] = True
        self.assertFalse(summarize(spec, events)["finished"])
        spec["budgetLimit"] = 26
        with self.assertRaises(ValueError):
            summarize(spec, events)


    def test_quality_regrade_preserves_live_trace_and_rejects_wrong_answer(self):
        spec, events = self.fixture()
        event = next(e for e in events if e["type"] == "case_terminal")
        event.update(outcome="QUALITY_FAILED", usable=False, source_hash="source")
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            (path / "spec.json").write_text(json.dumps(spec), encoding="utf8")
            trace = "\n".join(json.dumps(e) for e in events).encode()
            (path / "events.jsonl").write_bytes(trace)
            (path / "private-answers").mkdir()
            answer = '{"schemaVersion":1,"sessions":[]}'
            answer_path = path / "private-answers/p-a.json"
            answer_path.write_text(json.dumps(dict(raw_model_json=answer)), encoding="utf8")
            audit_path = path / "audit.json"
            audit = dict(parent_trace_sha256=hashlib.sha256(trace).hexdigest(), method="offline replay",
                cases=[dict(profile="p", case_id="a", response_sha256=hashlib.sha256(answer.encode()).hexdigest(),
                            source_hash="source", outcome="SUCCEEDED", usable=True, audited=True, checkpoint_failures=0)])
            audit_path.write_text(json.dumps(audit), encoding="utf8")
            result = write_report(path, audit_path)
            self.assertEqual((path / "events.jsonl").read_bytes(), trace)
            self.assertEqual(result["profiles"][0]["batch_ms"], 120)
            self.assertEqual(result["profiles"][0]["usable"], 1)
            self.assertIsNone(result["profiles"][0]["first_usable_ms"])
            answer_path.write_text(json.dumps(dict(raw_model_json="changed")), encoding="utf8")
            with self.assertRaises(ValueError):
                write_report(path, audit_path)

    def test_incomplete_concurrency_curve_does_not_recommend_measured_subset(self):
        p = dict(id="c2", runtime="lightweight", concurrency=2, eligible=True, completed=True, warmup=False,
                 load_model="fixed_work", cases=["a"], request_hashes={"a": "same"}, period="pilot", round=0,
                 batch_ms=100, usable_per_minute=600)
        later = dict(p, id="c4", concurrency=4, eligible=False, completed=False)
        decision = recommend(dict(mode="live", platform="jvm-robolectric"), [p, later])
        self.assertFalse(decision["concurrency_curve_complete"])
        self.assertIsNone(decision["exploratory_concurrency"])


class PreparationTests(unittest.TestCase):
    def test_rounds_remain_in_one_period_and_runtime_comparison_fixes_concurrency(self):
        profiles = study_profiles("runtime", ["a", "b"], "same-period", rounds=3, concurrency=4)
        self.assertEqual(len(profiles), 6)
        self.assertEqual({p["period"] for p in profiles}, {"same-period"})
        self.assertEqual({p["concurrency"] for p in profiles}, {4})
        self.assertEqual(profiles, study_profiles("runtime", ["a", "b"], "same-period", rounds=3, concurrency=4))
        self.assertEqual(len({p["id"] for p in profiles}), 6)

    def test_preparation_does_not_read_credentials_or_execute_anything(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            data = root / "dataset.json"
            data.write_text(json.dumps(replay_dataset(REPO)), encoding="utf8")
            with patch("benchmark.execute") as jvm, patch("device.execute_device") as device, patch("benchmark.getpass.getpass", side_effect=AssertionError):
                main(["live", "--dataset", str(data), "--base-url", "https://api.ppio.com/openai/v1", "--model", "fixture",
                      "--device", "--prepare-only", "--stage", "concurrency", "--rounds", "3", "--budget-limit", "1500",
                      "--key-file", str(root / "missing-credential.txt"), "--output", str(root / "output")])
                jvm.assert_not_called()
                device.assert_not_called()
            spec = json.loads((root / "output/plan.json").read_text(encoding="utf8"))
            self.assertEqual(spec["maxRequests"], 96)
            self.assertEqual(spec["budgetLimit"], 1500)
            self.assertEqual(spec["platform"], "android-device")
            self.assertEqual({p["period"] for p in spec["profiles"]}, {"pilot"})
            self.assertNotIn("apiKey", spec)

    def test_device_key_never_enters_argv_and_is_cleaned_when_trace_is_missing(self):
        from device import execute_device, DEVICE_ROOT
        from unittest.mock import MagicMock
        with tempfile.TemporaryDirectory() as folder:
            repo = Path(folder)
            apk = repo / "app/build/outputs/apk/benchmark/app-benchmark.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"fixture-apk")
            output = repo / "results"
            key = "fixture-private-credential"
            device = MagicMock()
            device.environment.return_value = dict(wakefulness="Awake", low_power="0", thermal_status="0")
            pushed_credentials = []
            def call(*args, **kwargs):
                self.assertNotIn(key, args)
                if args[0] == "push" and ".bench-key-" in str(args[-1]):
                    path = Path(args[1])
                    self.assertEqual(path.read_text(encoding="utf8"), key)
                    pushed_credentials.append(path)
                return subprocess.CompletedProcess(args, 0, "INSTRUMENTATION_CODE: -1\n" + key, "")
            device.call.side_effect = call
            with patch("device.find_adb", return_value="fixture-adb"), patch("device.Device", return_value=device), patch("device.subprocess.run"):
                with self.assertRaises(RuntimeError):
                    execute_device(repo, dict(mode="live", maxRequests=2), output, key)
            self.assertTrue(pushed_credentials)
            self.assertTrue(all(not p.exists() for p in pushed_credentials))
            self.assertNotIn(key, (output / "instrumentation.txt").read_text(encoding="utf8"))
            self.assertNotIn(key, (output / "submitted-spec.json").read_text(encoding="utf8"))
            cleanup = [c for c in device.call.call_args_list if c.args[:3] == ("shell", "rm", "-f")]
            self.assertTrue(cleanup)
            self.assertTrue(all(str(p).startswith(DEVICE_ROOT + "/") for p in cleanup[-1].args[3:]))


if __name__ == "__main__":
    unittest.main()
