# Statistics, exercise history and aliases

View totals from local records and current valid Markdown confirmations, inspect exercises and follow a value back to its source record.

## Sub-features

- `insight-local`: local records contribute without a connected library.
- `insight-ranges`: 30/90/365 days and all-time ranges, plus the displayed weekly training distribution.
- `insight-focus`: select up to three focus exercises and reopen their history.
- `insight-source`: follow statistics, history and day views to the source record UUID.
- `insight-alias`: save a global display name for selected exercise aliases.

## How to get to it (user POV)

- Bottom Insight tab.
- Exercise history: focus card, Sets by exercise row, or All exercises sheet.
- Sources: Latest record, chart source row, recent diary row, achievement row, or View by day.
- Aliases: exercise history → Manage aliases.

## Driving it with Android CLI + ADB

Preconditions: create `VerifySquat` with 8 repetitions through the local-record journey. For a numeric weight chart, also enter a known kg load with Total weight basis; use two comparable dates for change/record claims. A single unweighted set only proves local totals and source navigation.

- **Local totals.** With the executable prefix from the index, `tap --run smoke --text 'Insight'`, then `tap --run smoke --text 'All'` (scroll if needed). Inspect `Sessions`, `Recorded sets` and the fixture exercise. An isolated one-record dataset shows one session and one set; on Pixel_10 compare fixture contributions with the retained baseline rather than asserting global counts.
- **Range controls.** Independently tap `30 days`, `90 days`, `365 days` and `All`, inspecting the resulting totals and visible date scope. Record the actual fixture dates.
- **Choose focus.** Under `Focus exercises`, `tap --run smoke --text 'Choose'`, select `VerifySquat`, then `tap --run smoke --text 'Save'`. Reopen selection after `restart --run smoke`; require the selection remains checked.
- **Open history.** Tap `VerifySquat` from its focus card, the `Sets by exercise` row and the `All exercises` sheet in separate passes. Inspect the actual matching controls before using `--index`. `tap --run smoke --text 'Show recorded sets'`; require 8 repetitions.
- **Follow source.** For the default unweighted fixture, expand `Show recorded sets`, inspect its occurrence's clickable date/title row, and tap that exact displayed date/title with `tap --run smoke --text` (not the exercise-name heading below it). Require native `Training record` with `VerifySquat` and 8. `Latest record` is a separate route that needs comparable numeric weight or bodyweight data; with that fixture, tap it and require the correct source. For Markdown fixtures, source links must open the original diary's confirmed analysis. Repeat View by day and any displayed chart/recent/achievement source route separately.
- **Alias.** This branch needs a second local exercise name `VerifySquatAlt`, created through the local-record editor. In exercise history, `tap --run smoke --text 'Manage aliases'`, inspect the sheet, and select both names in its checkbox list. Under `Display name`, choose the `VerifySquatAlt` radio row (inspect its selected state and use `--index` only after locating the lower radio row, since the same name also labels a checkbox above). `tap --run smoke --text 'Save aliases'`. Restart, reopen Insight and assert the two names group under `VerifySquatAlt` and the radio choice persists. Folder switch/disconnect should not reset global aliases.
- **Proof.** `capture --run smoke --name insight-source`; inspect screenshot. Retain displayed totals, alias selection, returned source layout and record UUID/database evidence.

## Gotchas

- One unknown-weight set must not generate a numeric weight curve. Named positive repetitions still contribute to counts.
- Alias grouping needs at least two exercise names; the one-record smoke alone does not supply that precondition. `Display name` is a heading above radio choices, not an editable text field.
- Two dates and compatible weight meaning are prerequisites for change/PR claims; missing comparison data is not a zero result.
- Charts use Canvas with descriptive semantics. Prefer textual points and source links over guessed chart coordinates.
- Source, selection and alias sheets have repeated names; inspect scope before selecting. One source route does not prove the other listed routes.
- Existing alias help text may imply folder scope; the project contract requires global aliases. Verify persistence across folder changes before claiming this behavior.
- Focus choices are library-specific (including local-only); aliases are global. On shared data retain range/focus preferences, modify only fixture aliases, and restore preferences/remove fixture aliases through UI after proof.
