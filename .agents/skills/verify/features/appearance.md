# Appearance preferences

Choose light, dark or system appearance and optional dynamic color, with preferences surviving an App restart.

## Sub-features

- `appearance-light`, `appearance-dark`, `appearance-system`: persist theme selection.
- `appearance-dynamic`: persist the dynamic color switch on Android 12+.

## How to get to it (user POV)

Today → Settings (gear content description) → Appearance.

## Driving it with Android CLI + ADB

Preconditions: a bound device with a successful doctor. Check actual SDK support for dynamic color (Android 12+). Record the initial theme and switch values for restoration on shared data. No seed data, folder, provider or auth is required.

- **Open.** With the executable prefix from the index, `tap --run smoke --text 'Settings'`, then `tap --run smoke --text 'Appearance'`; inspect `Follow system`, `Light`, `Dark`, and `Dynamic color`.
- **Light.** `tap --run smoke --text 'Light'`; `layout --run smoke` must show the Light radio selected. `capture --run smoke --name appearance-light`, immediately inspect the PNG, return via `Back`, reopen and assert the selected state.
- **Dark.** `tap --run smoke --text 'Dark'`; assert radio selection, capture/inspect `appearance-dark`, then `restart --run smoke`. Navigate Settings → Appearance; Dark must remain selected.
- **System.** `tap --run smoke --text 'Follow system'`; assert radio selection persists after restart/reopen. Matching the current light/dark screenshot alone does not prove system mode was selected.
- **Dynamic color.** Inspect the current switch's checked state, `tap --run smoke --text 'Dynamic color'`, verify the opposite state, restart/reopen and verify it persists. Capture before/after switch semantics and screenshots.
- **Proof.** Keep layouts, selected/checked states, screenshots and restart transcript. Restore the initial theme and dynamic switch through UI, then cleanup the bound run.

## Gotchas

- Controls can be disabled while loading or saving; wait for the real enabled state.
- Theme selection and dynamic color are distinct preferences. A color change alone cannot establish switch persistence.
- UI proof does not authorize modifying the protected bottom toolbar/FAB implementation.
