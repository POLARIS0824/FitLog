# AI extraction and local confirmation

Extract a candidate from a Markdown original, review it, and save local corrections while preserving both the original and candidate.

## Sub-features

- `ai-configure`: configure provider/model and retain settings after save.
- `ai-single`, `ai-batch`: individual and range-based extraction with cancellation/failures.
- `review-single`, `review-batch`: inspect and confirm candidates through both routes.
- `review-correct`: edit a saved confirmation and retain it after candidate changes.
- `review-revoke`: remove local confirmation while retaining original and candidate.

## How to get to it (user POV)

- Single: Log → Markdown → filename → Analysis results → Extract with AI.
- Batch: Log → Markdown → Filters and analysis → Analyze diaries → range → Start analysis.
- Review: expanded tools → Awaiting confirmation: N → Review candidates; Review in diary opens the candidate individually.
- AI settings: Today → Settings → AI settings, diary Diary options → AI settings, or a configuration-error action.

## Driving it with Android CLI + ADB

Preconditions: connected known fixture with unchanged source hash, authorized provider credentials/network and permission to send that fixture; alternatively an explicitly configured mock at the existing provider HTTP boundary. No provider or candidates are seeded by the default launch. Never log credentials or include them in command transcripts.

- **Configure.** Using the prefix from the index, `tap --run smoke --text 'Settings'`, `tap --run smoke --text 'AI settings'`; inspect live field labels and enter the approved endpoint/model/key interactively without putting secrets into evidence. Save, reopen and verify non-secret settings persisted. A connection test is not extraction proof.
- **Extract individually.** Open the fixture, `tap --run smoke --text 'Analysis results'`, `tap --run smoke --text 'Extract with AI'`; wait for a candidate, not only a finished request. Capture candidate values, notices and provenance without exposing private payloads.
- **Review.** Choose the actual editable repetitions value from `layout --run smoke` (semantic labels start with `Edit` and include the field and current value). In its picker, `tap --run smoke --text 'Enter exact value'`, inspect/focus the field, replace its current contents with 8 using the visible editor controls, dismiss the IME, then `tap --run smoke --text 'Done'` to apply. The page action now reads `Save corrections`: tap it, inspect `Training date`, select a valid date if necessary, and tap the dialog's `Confirm and save`. Require `Confirmed corrections saved` and `User-confirmed record`. An unedited candidate instead has page action `Confirm and save`.
- **Persist correction.** `restart --run smoke`; navigate back through Log → Markdown → filename → Analysis results. Require the edited value in the confirmed view and inspect read-only persisted rows. Hash the original again.
- **Batch extraction.** In Log → Markdown, `tap --run smoke --text 'Filters and analysis.*'`, `tap --run smoke --text 'Analyze diaries'`, choose `All diaries` and `tap --run smoke --text 'Start analysis'`. Observe counts and per-file outcomes. Keep unrelated search/status filters active to verify they do not redefine the selected analysis range.
- **Batch review.** Tap the displayed `Awaiting confirmation: .*`; inspect each candidate, select only reviewed candidates and tap the displayed `Confirm selected .*`. Reopen those diaries individually and assert their confirmation values. A diary with existing corrections must require individual replacement.
- **Revoke.** In a confirmed diary's `Diary options`, choose `Remove confirmation`, then confirm. Reopen: original and candidate remain available, confirmed values no longer contribute to Insight.
- **Proof.** `capture --run smoke --name confirmed-review`; inspect screenshot; retain before/after source hashes, candidate versus confirmed layouts, actions and persisted rows. Report the selected provider boundary and entry points.

## Gotchas

- Without approved credentials or a candidate, this journey is blocked by missing preconditions. Showing a configuration error does not satisfy extraction/review.
- A real provider may charge and receives source content. The generated skill does not grant permission to use private files or credentials.
- Partial results need explicit acknowledgement. Unknown values remain unknown; never silently fill them to obtain a pass.
- Date-less filenames need an explicit training date. Save verifies the current source version; source changes must not overwrite existing human corrections.
- Candidate chips and review controls are conditional. Reinspect after extraction instead of tapping labels from a different state.
