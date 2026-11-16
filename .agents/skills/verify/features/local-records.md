# Local training records

Create and edit local training without a Markdown folder or AI provider, then return to the same saved record from Log.

## Sub-features

- `local-blank`: opening and leaving an untouched editor creates no record.
- `local-create-today`, `local-create-fab`, `local-create-log`: all create entry points reach the same editor.
- `local-autosave`: first input starts autosave, including incomplete records; a named exercise and positive repetitions count toward totals, with unknown weight allowed.
- `local-date`: edit the training date through the editor's date picker and retain it after reopen.
- `local-edit-sets`: add/copy/remove sets, weight meaning, exercise/record notes and undo.
- `local-rest-delete`: explicitly mark rest and confirm record deletion.
- `local-reopen`: same UUID and values survive process restart and reopen.

## How to get to it (user POV)

- Today → Record training.
- Any of Today, Log or Insight → Open menu → Record training.
- Log → Local records → Record training.
- Existing record: Log → Local records → row with its date and exercise name.

## Driving it with Android CLI + ADB

Preconditions: a bound healthy emulator, English UI and a unique fixture name. Capture existing UUIDs on Pixel_10; use the isolated baseline for exact empty-list/database-count assertions. Bundled smoke requires `launch --isolated` and is rejected on shared devices.

- **Bundled proof.** Run `python .agents/skills/verify/scripts/verify.py smoke --run smoke`. It verifies blank cancellation, creates `VerifySquat` with 8 repetitions from Today, waits for `Saved locally`, reads `1 completed set` in Log, reopens, restarts, reopens again, and inspects SQLite. Examine its checkpoint screenshot before any further action.
- **Today entry.** `tap --run smoke --text 'Record training'` from Today; `wait --run smoke --text 'Saved automatically'` proves the empty editor is ready. Use the full Python prefix from the index for these and all following commands.
- **FAB entry.** From each root tab, `tap --run smoke --text 'Open menu'`, then `tap --run smoke --text 'Record training'`; require `Training record`, then leave with `tap --run smoke --text 'Back'` without editing.
- **Log entry.** `tap --run smoke --text 'Log'`, `tap --run smoke --text 'Local records'` if needed, then `tap --run smoke --text 'Record training'`; require `Training record`.
- **Enter a set.** `tap --run smoke --text 'Add exercise'`, `tap --run smoke --text 'Exercise name'`, `input --run smoke --value VerifySquat`, `back --run smoke` to hide the IME, `tap --run smoke --text 'Repetitions'`, `input --run smoke --value 8`, `back --run smoke`, `wait --run smoke --text 'Saved locally'`.
- **Observe persistence.** `tap --run smoke --text 'Back'`, `tap --run smoke --text 'Log'`, `wait --run smoke --text 'VerifySquat'`, `wait --run smoke --text '1 completed set'`; tap the name to reopen. `restart --run smoke`, then Log and the same name must show 8 repetitions again. Use `database --run smoke` to verify `confirmed_diary.origin=NATIVE`, the same UUID, `confirmed_exercise.rawName=VerifySquat`, and `confirmed_set.reps=8`.
- **Sets and weight.** `tap --run smoke --text 'Set 1 options'` → `tap --run smoke --text 'Weight settings'`; choose a displayed unit/basis, `tap --run smoke --text 'Done'`, and reopen the sheet to assert it. `Exercise options` → `Copy last set`, or `Set 1 options` → `Copy this set` / `Remove set`; assert the set count changes and `Undo` restores a removal. `Add set` may need `--index 0` after layout inspection.
- **Explicit rest.** In a fresh editor `tap --run smoke --text 'Mark rest day'`; await saved and reopen from Log to assert `Rest day`. Removing the last exercise must not produce rest automatically.
- **Delete.** In an existing fixture record `tap --run smoke --text 'Delete record'`, inspect the confirmation, then tap the dialog's `Delete record`; restart and verify the row and its database UUID are absent.
- **Date.** Open the displayed date button, choose a valid date through the actual picker and confirm. Reopen after restart and verify the saved date and unchanged record UUID.
- **Evidence.** `capture --run smoke --name local-reopened`; inspect PNG; retain the action log, layout, snapshot and UUID. Delete only run-owned fixture records through UI, then cleanup the bound run.

## Gotchas

- New exercise already includes one empty set. Do not add another just to enter the first repetitions.
- Weight and repetitions both have content description `Set 1`; that selector is ambiguous. Use their visible labels and inspect the live tree.
- A saved message alone is insufficient: reopen after restart and inspect stored rows. An incomplete record saves but only a named exercise with positive integer repetitions counts.
- Notes fields begin collapsed behind `Training notes` and `Exercise notes`. The date button shows today's actual date, not a fixed fixture date.
- A save failure must retain input and expose manual retry. Simulating a repository failure through an internal setter is not a UI proof of that branch.
