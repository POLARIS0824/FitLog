---
name: r8-analyzer
description: Analyzes Android build files and R8 keep rules to identify redundancies,
  broad package-wide rules, and rules that subsume library consumer keep rules. Use
  when developers want to optimize their app's size, remove redundant or overly broad
  keep rules, or troubleshoot Proguard configurations.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-05-19'
  keywords:
  - R8
  - proguard
  - keep rules
  - app size
  - optimization
---

## Step 1. Setup and configuration check

- Inspect `build.gradle`, `build.gradle.kts`, and `gradle.properties`.
- Use [references/CONFIGURATION.md](references/CONFIGURATION.md) to identify missing optimizations.
- **AGP** : If \< 9.0, suggest migration to 9.0 for [build time improvement
  performance](references/android/topic/performance/app-optimization/enable-app-optimization.md)
- **Full Mode** : Verify `android.enableR8.fullMode=false` is removed from gradle.properties.

## Step 2. Analysis path selection

- Reuse the build configuration inspected in Step 1; inspect `libs.versions.toml` if needed to determine the R8 version

- **If quantitative impact analysis is needed and supported by R8 \>= 9.3.7-dev** : Use **Path A (Quantitative)** when its build/tooling prerequisites are available. A narrow static keep-rule review may use Path B without generating metrics.

- **If R8 \< 9.3.7-dev** : Proceed to **Path B (Heuristic)**.

### Path A: Quantitative data generation (R8 \>= 9.3.7-dev)

- **Check requirements** : Python and `protobuf` package are mandatory.
- **Generate and analyze** : If Path A is selected, use the commands appropriate to the host shell in [references/CONFIGURATION-ANALYZER.md](references/CONFIGURATION-ANALYZER.md) to generate the proto file using R8 configuration analyzer, convert it to json and analyze the result.
- **Report** : Rely entirely on the generated file `analysis.txt` for scores and rule impact metrics. Proceed to Step 3.

### Path B: Scoped static evaluation and recommendation

*(Use for scoped static reviews or when quantitative generation is unavailable; do not claim measured metrics.)*

- **Manual evaluation** : Inspect `proguard-rules.pro`.
- **Library check** : Compare rules against [references/REDUNDANT-RULES.md](references/REDUNDANT-RULES.md). Suggest **Remove** for bundled rules.
- **Custom rule check** : Use [references/KEEP-RULES-IMPACT-HIERARCHY.md](references/KEEP-RULES-IMPACT-HIERARCHY.md) and [references/REFLECTION-GUIDE.md](references/REFLECTION-GUIDE.md) to prioritize and evaluate. Suggest **Refine** for broad rules (for example, package-wide).
- **Validation** : Suggest Macrobenchmark tests using [UI Automator](references/android/training/testing/other-components/ui-automator.md) for any proposed changes. Proceed to Step 3.

## Step 3. Report generation

- **Format** : For a full quantitative report, use [references/REPORT_FORMAT.md](references/REPORT_FORMAT.md). For a focused review, return concise actionable findings in the user's requested format; do not load the full report template.
- **Input**: Extract metrics (Scores, Impacts, Example Classes) directly from generated file analysis.txt if using Path A, or from manual findings if using Path B.
- **Output** : State findings, validation, and material limitations. Identify whether impact claims are measured or heuristic; do not imply a build or runtime check ran when it did not.

## Constraints

- **Output scope**: Match the requested review/report format and omit empty sections.
- **No code changes**: Research and suggest only; Do not modify files.
- **No redundancy**: Avoid generic R8 tutorials; include relevant rule references when needed to explain a finding.
- **Focus**: Omit sections (for example, Subsumed Rules, Configuration) if no issues or items are found.
