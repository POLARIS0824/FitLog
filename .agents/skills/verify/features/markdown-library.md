# Read-only Markdown library

Connect one folder through Android's document picker, browse Markdown originals and refresh their index without changing the source files.

## Sub-features

- `vault-connect`: connect from every import/connect entry point; navigate only after saving succeeds.
- `vault-scan`: recursive scan, complete/partial/failure distinctions, empty folder.
- `vault-find`: filename/path search, clear, sorting and status filters.
- `vault-original`: read the original even when parsing has not succeeded.
- `vault-disconnect`: disconnect/switch without losing originals, native records or saved confirmations.

## How to get to it (user POV)

- Today → Import Folder.
- Any root tab → Open menu → Import Folder.
- Log → Markdown → Connect folder, in the empty state or Diary options menu.
- Today → Settings → Vault management → Connect, switch or authorize folder.

## Driving it with Android CLI + ADB

Preconditions: bound healthy emulator; a uniquely named run-owned folder under Download containing `2026-10-23-training.md` and `nested/2026-10-22-rest.md`, with known original bytes/hashes. Read the bound serial from `build/verification/<run>/run.json`; use explicitly targeted ADB push only to stage these fixtures outside user folders. On shared data record the current folder selection before switching, then restore it through SAF after proof. No AI is needed for browsing.

- **Connect through UI.** With the Python prefix from the index, `tap --run smoke --text 'Import Folder'`, `wait --run smoke --text 'Connect a Markdown source'`, `tap --run smoke --text 'Choose'`. Run `layout --run smoke` and select Download → FitLogVerify → the picker’s displayed folder-use/allow controls. System picker labels depend on OS; copy them from the live tree. On return `tap --run smoke --text 'Confirm'`.
- **Assert scan.** `wait --run smoke --text 'Scan complete'`; require both fixture filenames in Log → Markdown (scroll if needed). A partial scan is not a passing full-scan proof.
- **Search.** `tap --run smoke --text 'Search filenames and paths'`, `input --run smoke --value 2026-10-23`; inspect that only the matching fixture remains. `tap --run smoke --text 'Clear search'` restores both. Search `NoSuchDiary` and require `No matching notes\.`.
- **Read original.** `tap --run smoke --text '2026-10-23-training\.md'`; require `Original` and the known fixture content. Parsing is not a precondition for viewing original text.
- **Refresh and sorting.** Return to Log → Markdown and `tap --run smoke --text 'Refresh'`; require complete scan and unchanged file hashes. Expand `Filters and analysis.*` to exercise displayed status chips. Sorting has its own filename button in these tools: tap the current `Filename ↑` or `Filename ↓`, then choose the desired order from its menu and assert row order. `Diary options` contains folder actions, not sorting.
- **Alternate connection routes.** Independently open `Open menu` → `Import Folder`, Markdown empty/menu `Connect folder`, and Today → `Settings` → `Vault management` → `Connect, switch or authorize folder`; each must reach the setup UI. Record which routes were driven, even if the same fixture is chosen.
- **Disconnect.** Vault management → `Disconnect current vault`, inspect and confirm the actual dialog. Revisit Log → Local records and reopen the native fixture. Hash source files again; confirm the Markdown tab requires connection.
- **Proof.** `capture --run smoke --name markdown-files`, visually inspect it, retain hashes plus SAF actions and list/original layouts. Disconnect the fixture, restore any original folder selection, and remove only run-owned fixture files after evidence is retained. Shared AVD cleanup retains its files and data.

## Gotchas

- Empty fresh launch has no folder fixtures; connection/scanning journeys fail their precondition until fixtures are staged. An empty directory only proves the empty-folder branch.
- Selecting a folder through SAF is part of the journey. Writing URI preferences or injecting grants bypasses it.
- Android may prohibit selecting the Download root; choose the dedicated child folder. Persisted permission is read-only.
- `.md` and `.markdown` files are included; dot-prefixed directories are skipped. Partial scans must not mark unseen files missing.
- Source updates, read-only filesystem behavior, reauthorization and provider-specific failures need dedicated fixtures. Do not claim them from one complete scan.
