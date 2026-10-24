---
name: verify
description: Launch and drive FitLog on the existing Pixel_10 by default, with an explicit isolated AVD for empty-data journeys. Retain screenshots, action transcripts, APK identity and database evidence for local records, Markdown, analysis, insights and appearance.
---

# Verify FitLog

The primary surface is the native Jetpack Compose Android app, `com.example.fitlog/.app.MainActivity`. There is no web server, login, or seed requirement for local training records. Read [the feature index](features/README.md) and the relevant journey before driving. This skill uses Android CLI accessibility inspection and ADB input, not internal ViewModel setters.

## Launch

Run from the repository root in Nushell. Python runs the Windows-specific emulator/build tools. Prerequisites: JDK 21, the repository's Android SDK (`local.properties` or `ANDROID_HOME`), Python 3, and `android` CLI on PATH. The helper uses the checked-in Gradle wrapper. On a sandboxed host, Gradle/SDK cache access may need the normal execution escalation.

### Preferred device for ordinary checks

Prefer the user's existing `Pixel_10` AVD at `D:/Code/Android/.android/avd/Pixel_10.avd`. `ANDROID_AVD_HOME` is `D:/Code/Android/.android/avd`; `D:/Code/Android/Avd` is not its configured location. Recheck the environment and AVD identity each run. Its image is `system-images/android-37.2/google_apis_playstore_ps16k/x86_64` in the configured SDK.

Use the helper to reuse an already running Pixel_10, or start that existing AVD with SDK emulator.exe. No image download or replacement AVD is automatic:

```nu
python -B .agents/skills/verify/scripts/verify.py launch --run daily
```

This opens the installed app without clearing data. An existing activity may retain its current page: inspect layout before acting. Use `launch --run daily-build --install` to build this checkout with Gradle and deploy its exact APK through `android run`. Plain launch records the existing installation and does not establish that it matches current sources. Installation failures require diagnosis; never resolve them by uninstalling or wiping shared data.

To attach without starting an app or installing anything, observe the serial with `adb devices -l`, leave FitLog foreground, then run `attach --run attached --device <observed-serial>`. `--avd` defaults to `Pixel_10`; explicit serials must match. Physical phones are rejected. The verified serial, AVD, build identity and lifecycle ownership are saved in `build/verification/<run>/run.json`; all doctor/drive/cleanup commands use that binding. A device lease prevents two helper runs from driving the same serial. Cleanup the lease holder before a new run.

Preserve this AVD and its app data. Never wipe, uninstall, delete its directory, or run the empty-data `smoke` against it. Do not double-drive with another agent or an active user. Reused instances stay running; only an instance started by this run is stopped on cleanup, with AVD files retained. Use `doctor --run daily` and substitute the actual run name for the isolated `--run smoke` examples below.

### Disposable device for clean-data journeys

Use this mode for the bundled smoke, destructive fixture operations, parallel verification, or checks whose contract requires empty app data. An unavailable preferred device is a reason to diagnose it, not silently download another image or substitute a different device.

```nu
android sdk install system-images/android-36/default/x86_64
python -B .agents/skills/verify/scripts/verify.py launch --run smoke --isolated
```

The first command installs a reusable SDK dependency; omit it when that image is already installed. `launch --isolated` builds Debug, creates an empty private AVD under `build/verification/smoke/scratch/`, boots on port 5580, deploys the exact APK and opens Today. It refuses occupied ports and existing run directories. Use a fresh run name each attempt. No existing AVD or phone is driven. Ready requires completed boot, matching APK hash, resumed activity and Today's `Record training`.

The run directory is printed. Keep it for all commands below. On failure, run cleanup before starting another attempt:

```nu
python .agents/skills/verify/scripts/verify.py cleanup --run smoke
```

## Doctor

```nu
python .agents/skills/verify/scripts/verify.py doctor --run smoke
```

This instance check requires lease ownership, the bound AVD name, Android boot completion, the recorded installed APK SHA-256, a live FitLog PID, and FitLog as the resumed activity. It writes `doctor.json` in the run's evidence directory. Run it before driving and after failed actions or surprises; failed commands require doctor before further drives. `restart` returns to a known app state and rechecks health. Use `doctor --allow-picker` when the real Android SAF picker is foreground; other packages still fail. A healthy process does not resolve a wedged UI. A successful build alone is not UI acceptance.

## Drive

```nu
python .agents/skills/verify/scripts/verify.py layout --run smoke
python .agents/skills/verify/scripts/verify.py tap --run smoke --text 'Record training'
python .agents/skills/verify/scripts/verify.py tap --run smoke --text 'Add exercise'
python .agents/skills/verify/scripts/verify.py tap --run smoke --text 'Exercise name'
python .agents/skills/verify/scripts/verify.py input --run smoke --value VerifySquat
python .agents/skills/verify/scripts/verify.py back --run smoke
```

`tap` refreshes the accessibility hierarchy and resolves visible text/content descriptions to current bounds. It fails on ambiguity unless `--index` explicitly selects a match. `input` requires an editable field focused in the current hierarchy; use ASCII fixture values. Inspect after each UI change. Never guess fixed screen coordinates. `scroll --direction down` scrolls the current visible scroll container and records its bounds. `wait --text 'Saved locally'` waits for UI state, not a blind delay. Commands support `--help`.

Android CLI supplies layout, screen capture and APK deployment with the SDK explicitly selected. ADB supplies targeted input, health, restart and database extraction. Installed Android CLI 1.0.16500706 has no `android input`; `layout --diff` is deprecated/no-op. Windows startup uses SDK emulator.exe because Android CLI's emulator support has platform limitations and lacks private-port controls. Android CLI's user-directory lock may require normal sandbox escalation.

The fully scripted local-record journey requires `launch --isolated`; shared runs reject `smoke` before any UI action:

```nu
python .agents/skills/verify/scripts/verify.py capture --run smoke --name launch-ready
```

Visually inspect `build/verification/smoke/evidence/launch-ready.png`, then run:

```nu
python .agents/skills/verify/scripts/verify.py smoke --run smoke
```

It opens the editor from Today, verifies a blank editor writes no record, enters one named exercise with 8 repetitions, waits for auto-save, returns through Log, reopens the record, restarts the process and reopens it again, and checks the persisted database. It finishes with `local-reopened.png`; inspect that screenshot before another action. This proves that entry point only. The map records other entry points and features separately.

## Evidence

Artifacts survive cleanup under `build/verification/<run>/evidence/`: `actions.jsonl` (commands, outputs, failures and timestamps), accessibility JSON, screenshots at named checkpoints, exact APK hash, Git HEAD and dirty paths, doctor output, and database snapshots/SQL results. The parent directory is ignored build output, not source control.

Capture before/action/after, not just a final screen. A mutation needs a second user-facing view after process restart plus a read-only database assertion. Never seed records by SQL or write test-only app state. For Markdown use run-owned fixtures outside any user vault, record their hashes before/after, and select them through the real SAF picker. AI proof requires an authorized provider or a mock at the existing HTTP provider boundary; never infer extraction success from configuration or an error screen. Secrets and provider payloads do not belong in evidence. Report missing preconditions and untested entry points explicitly.

Use `capture --name checkpoint` for a layout plus screenshot; visually inspect its PNG before another action. `database` stops the bound app, copies closed SQLite state and reads it read-only; call only after saves finish, then `restart`. Snapshots get unique timestamps by default; `database --name baseline` chooses a fresh named directory and refuses overwrite. On Pixel_10 retain a baseline, use unique fixture names and prove UUIDs/deltas instead of global empty counts. Restore preferences, remove only run-owned records through UI, and restore any prior folder selection after fixture proof. Never overwrite preexisting aliases or user data.

## Cleanup

```nu
python .agents/skills/verify/scripts/verify.py cleanup --run smoke
```

Cleanup checks captured evidence survives and releases only this run's device lease. Attached/reused instances stay running; an instance started by this run is stopped by its verified serial. Shared AVD files and app data remain. Isolated cleanup additionally waits for the owned emulator process and removes only its validated scratch directory. It never deletes proof, clears a shared app or stops the ADB server. Restore run-owned UI fixture effects before cleanup; cleanup does not silently revert app data. Parallel isolated runs need different run names and ports.

## Helpers

All commands use [scripts/verify.py](scripts/verify.py). `launch`, `attach`, `doctor`, `layout`, `tap`, `input`, `back`, `scroll`, `wait`, `capture`, `restart`, `database`, `smoke`, and `cleanup` share `--run`. Use `--help` for arguments. Run `python -B -m unittest discover -s .agents/skills/verify/scripts -p test_verify.py` for device ownership, cleanup and APK identity regression checks. After app changes, use `/maintain-verification-skill` to update the map and rerun affected journeys.
