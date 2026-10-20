"""Native, foreground benchmark execution. Credentials never enter argv or reports."""
import json
import hashlib
import os
import re
import shutil
import subprocess
import tempfile
import uuid
from pathlib import Path

PACKAGE = "com.example.fitlog.benchmark"
DEVICE_ROOT = f"/sdcard/Android/data/{PACKAGE}/files"


def find_adb(repo, explicit=None):
    if explicit:
        return str(Path(explicit).resolve(strict=True))
    if found := shutil.which("adb"):
        return found
    properties = (repo / "local.properties").read_text(encoding="utf8")
    sdk = next(line.split("=", 1)[1] for line in properties.splitlines() if line.startswith("sdk.dir="))
    sdk = sdk.replace(r"\:", ":").replace("\\\\", "\\")
    return str((Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")).resolve(strict=True))


class Device:
    def __init__(self, adb, serial=None):
        self.adb = adb
        devices = subprocess.run([adb, "devices"], check=True, capture_output=True, text=True).stdout
        ready = [line.split()[0] for line in devices.splitlines()[1:] if len(line.split()) == 2 and line.split()[1] == "device"]
        if serial:
            if serial not in ready:
                raise ValueError("Selected device is not connected and authorized")
        elif len(ready) != 1:
            raise ValueError("Connect one authorized device or specify --device-serial")
        self.serial = serial or ready[0]

    def call(self, *args, check=True, timeout=60):
        return subprocess.run([self.adb, "-s", self.serial, *map(str, args)], check=check,
                              capture_output=True, text=True, encoding="utf8", errors="replace", timeout=timeout)

    def environment(self):
        battery = self.call("shell", "dumpsys", "battery").stdout
        power = self.call("shell", "dumpsys", "power").stdout
        thermal = self.call("shell", "dumpsys", "thermalservice").stdout
        def field(text, pattern):
            match = re.search(pattern, text)
            return match.group(1) if match else None
        return dict(serial=self.serial,
                    model=self.call("shell", "getprop", "ro.product.model").stdout.strip(),
                    sdk=self.call("shell", "getprop", "ro.build.version.sdk").stdout.strip(),
                    os=self.call("shell", "getprop", "ro.build.version.release").stdout.strip(),
                    abi=self.call("shell", "getprop", "ro.product.cpu.abi").stdout.strip(),
                    battery_level=field(battery, r"level:\s*(\d+)"),
                    battery_temperature_tenths_c=field(battery, r"temperature:\s*(\d+)"),
                    usb_powered=field(battery, r"USB powered:\s*(\w+)"),
                    thermal_status=field(thermal, r"Thermal Status:\s*(\d+)"),
                    wakefulness=field(power, r"mWakefulness=(\w+)"),
                    low_power=self.call("shell", "settings", "get", "global", "low_power").stdout.strip())


def execute_device(repo, spec, output, key=None, adb=None, serial=None):
    from benchmark import dump
    from reporting import write_report
    output = Path(output)
    if (output / "spec.json").exists() or (output / "events.jsonl").exists():
        raise ValueError("Device output directory already used")
    if spec["mode"] == "live" and not key:
        raise ValueError("Live device execution requires a separate credential")
    device = Device(find_adb(repo, adb), serial)
    # The APK is always rebuilt/up-to-date checked before use. No paid request is made by Gradle.
    subprocess.run([str(repo / "gradlew.bat"), "--no-daemon", ":app:assembleBenchmark"], cwd=repo, check=True)
    apk = repo / "app/build/outputs/apk/benchmark/app-benchmark.apk"
    device.call("install", "-r", str(apk), timeout=180)
    before = device.environment()
    before["apk_sha256"] = hashlib.sha256(apk.read_bytes()).hexdigest()
    if spec["mode"] == "live" and (before["wakefulness"] != "Awake" or before["low_power"] == "1"):
        raise ValueError("Unlock the phone, keep it awake and disable battery saver before live execution")
    if spec["mode"] == "live" and before["thermal_status"] is not None and int(before["thermal_status"]) >= 2:
        raise ValueError("Phone reports moderate or higher thermal stress; let it cool before starting a new measurement")
    # Only the benchmark package is stopped; production FitLog and its data are independent.
    device.call("shell", "am", "force-stop", PACKAGE)
    device.call("shell", "mkdir", "-p", DEVICE_ROOT)
    nonce = uuid.uuid4().hex
    output_name = f"run-{nonce}"
    spec_name = f"spec-{nonce}.json"
    key_name = f".bench-key-{nonce}.txt"
    remote_output = f"{DEVICE_ROOT}/{output_name}"
    dump(output / "device-environment-before.json", before)
    submitted = dict(spec, sourceRoot=None, outputDir=remote_output, platform="android-device", proxyUrl=None)
    dump(output / "submitted-spec.json", submitted)
    command = ["shell", "am", "instrument", "-w", "-r", "-e", "benchmarkSpec", spec_name,
               "-e", "benchmarkOutput", output_name, "-e", "benchmarkForeground", "true"]
    if spec["mode"] == "live":
        command += ["-e", "benchmarkLive", "true", "-e", "benchmarkCredential", key_name]
    command += [f"{PACKAGE}/com.example.fitlog.benchmark.BenchmarkInstrumentation"]
    result = None
    summary = None
    try:
        device.call("push", output / "submitted-spec.json", f"{DEVICE_ROOT}/{spec_name}")
        with tempfile.TemporaryDirectory(prefix="fitlog-bench-credential-") as folder:
            if key:
                credential_file = Path(folder) / "credential.txt"
                credential_file.write_text(key, encoding="utf8")
                device.call("push", credential_file, f"{DEVICE_ROOT}/{key_name}")
                credential_file.unlink()
            # Runtime timeouts and its hard budget bound requests. A host interruption stops the package.
            result = device.call(*command, check=False, timeout=max(240, spec["maxRequests"] * 125))
            log = result.stdout + result.stderr
            if key:
                log = log.replace(key, "[REDACTED]")
            (output / "instrumentation.txt").write_text(log, encoding="utf8")
    except (KeyboardInterrupt, subprocess.TimeoutExpired):
        device.call("shell", "am", "force-stop", PACKAGE, check=False)
        raise
    finally:
        # Exact, benchmark-owned paths only; never pull the credential directory.
        try:
            device.call("shell", "rm", "-f", f"{DEVICE_ROOT}/{key_name}", f"{DEVICE_ROOT}/{spec_name}", check=False)
            device.call("pull", f"{remote_output}/.", output, check=False, timeout=180)
            dump(output / "device-environment-after.json", device.environment())
        except (subprocess.SubprocessError, OSError) as error:
            dump(output / "device-collection-error.json", dict(error_class=type(error).__name__,
                result="Collection failed; do not rerun automatically. Cancel on the phone if it is still running."))
        if (output / "events.jsonl").exists() and (output / "spec.json").exists():
            summary = write_report(output)
    if result is None or result.returncode or "INSTRUMENTATION_CODE: -1" not in result.stdout or not summary or not summary["finished"]:
        raise RuntimeError("Device benchmark did not complete normally; inspect the saved partial trace")
    print(f"Native report: {output / 'report.html'}")
