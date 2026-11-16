# FitLog verification map

Read this index, then the feature file matching the changed behavior. This map describes the current working tree; a file is a journey specification, not a claim that every path passed.

## Baseline preconditions

- Ordinary checks prefer the existing Pixel_10 as described in the skill's Launch section. The baseline and commands below are for disposable clean-data journeys; never run the empty-list smoke against Pixel_10's existing data.
- Run `python .agents/skills/verify/scripts/verify.py launch --run smoke --isolated` for that empty Android 36 baseline. Ordinary `launch --run daily` binds Pixel_10 and retains existing data; use `--install` when testing current checkout code. Nothing needs authentication to create a local record.
- Commands abbreviate the executable as `python .agents/skills/verify/scripts/verify.py`. Feature recipes use `--run smoke` for the isolated baseline; substitute the actual bound run name (for example `--run daily`) on Pixel_10 and use unique fixtures plus baseline deltas.
- Require doctor before driving and after failures. SAF foreground checks use `doctor --allow-picker`. Never silently substitute a device. Use a new run name after cleanup; port 5580 is exclusive for a newly started instance.
- Local-record, statistics and appearance paths need no external service. Library paths require a run-owned fixture folder selected through SAF. Extraction paths require an explicitly authorized provider and data; a fresh device has neither.

## Driving conventions

- `layout --run smoke` obtains the live accessibility hierarchy. `tap --run smoke --text 'label'` selects a full regular expression against text/content description, then derives coordinates from current bounds. Multiple matches fail; choose `--index 0` only after inspecting the matching controls.
- Inspect after UI transitions. Use `wait --run smoke --text 'expected state'` for asynchronous saves/scans; `back --run smoke` sends Android Back, which closes the IME before leaving the editor.
- `input --run smoke --value VerifySquat` types into an already focused editable field. Use unique ASCII fixture names; the automated smoke uses `VerifySquat` in a fresh private device.
- Use `scroll --run smoke --direction down` for content outside the viewport. An ambiguous scroll container requires hierarchy inspection rather than guessing a swipe.
- English controls below come from `res/values/*_strings.xml`. The Chinese equivalents are in `res/values-zh/`; inspect the actual tree before adapting a selector. English success does not prove Chinese layout or strings.

## Proof and skip reporting

- Pair an action transcript with before/after hierarchy and screenshots. Visually inspect every screenshot immediately after capture.
- For mutations, reopen through a user-facing route after process restart and verify stored side effects. `database --run smoke` copies closed SQLite state read-only, stopping only this run's App; `restart --run smoke` opens it again.
- Keep evidence in `build/verification/<run>/evidence/`. Cleanup retains evidence and shared AVD/data, leaves reused instances running, and removes scratch only for an isolated run. Restore run-owned fixture effects/preferences through UI first. Run cleanup after failed attempts too.
- Name the feature ID and entry point proved. A missing fixture, candidate or credential is an unmet precondition. Do not replace the journey with an easier path or count unexecuted paths as passing.

## Features

- [Local records](local-records.md): creation from all three entry points, autosave, editing, sets, explicit rest, deletion and durable reopen. The bundled `smoke` drives the Today creation path.
- [Markdown library](markdown-library.md): all folder connection routes, recursive scanning, search, original view and disconnect.
- [Analysis and review](analysis-and-review.md): provider configuration, individual/batch extraction, candidate review and confirmation ownership.
- [Insights and aliases](insights-and-aliases.md): local-only totals, focus selection, weight history, source navigation and aliases.
- [Appearance](appearance.md): persisted light/dark/system choices and dynamic color.
