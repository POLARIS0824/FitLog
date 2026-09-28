---
name: git-commit
description: Inspect uncommitted working tree changes, group or split files into logical atomic commits, and craft Conventional Commit messages adhering to the repository's recent commit style and language (supporting bilingual Chinese/English).
---

# Git Commit

Guides the inspection of unstaged and untracked working tree changes, atomic commit planning, and message authoring aligned with repository history.

## Workflow

### 1. Inspect Status and History

Run the following commands before staging any files:

1. **Check uncommitted files:**
   ```bash
   git status -s
   ```
   Identify all modified (`M`), added (`A`), deleted (`D`), and untracked (`??`) files.

2. **Inspect recent repository style (last 3 commits):**
   ```bash
   git log -n 3 --oneline
   ```
   Analyze the recent commits to extract:
   - **Type & Scope pattern**: e.g., `feat(navigation):`, `fix(auth):`, or bare `feat:`.
   - **Language**: English, Chinese, or bilingual.
   - **Capitalization & Punctuation**: Lowercase vs title case, imperative mood, trailing period or not.
   - **Body format**: Whether multi-line bullet points or single-line messages are preferred.

3. **Inspect the actual diffs:**
   ```bash
   git diff
   ```
   Review the intent and semantic scope of the changes.

---

### 2. Group & Split Commits (Atomic Commits)

Do **NOT** default to `git add .` or blind single-batch commits. Evaluate whether changes should be split into multiple atomic commits:

- **Split when:**
  - Changes span unrelated features, bug fixes, or architecture layers (e.g., refactoring an existing UI component vs adding an entirely new data model).
  - Documentation, configuration, or dependency upgrades are mixed with business logic.
  - Large refactoring was performed alongside new user-facing functionality.
- **Group together when:**
  - All modifications serve a single cohesive intent (e.g., a feature implementation together with its direct unit test or component update).
  - Splitting would leave the repository in a broken or non-compiling intermediate state.

When splitting, define a clear sequence (e.g., Commit 1: prerequisite refactoring/fixes; Commit 2: new feature implementation).

---

### 3. Craft Conventional Commit Messages

Follow the **Conventional Commits** specification:
```text
<type>(<scope>): <summary>

[optional body with details]
```

#### Rules:
1. **Type selection:**
   - `feat`: New feature or user-visible enhancement.
   - `fix`: Bug fix.
   - `refactor`: Code change that neither fixes a bug nor adds a feature.
   - `style`: Formatting, missing semi-colons, white-space changes.
   - `docs`: Documentation only.
   - `perf`: Performance improvement.
   - `test`: Adding or correcting tests.
   - `build` / `ci`: Build system, Gradle/Maven, CI workflows.
   - `chore`: Maintenance, updating dependencies, cleanup.
2. **Align with Repository Convention:**
   - Respect the scope naming style found in the last 3 commits.
   - Respect the language convention. Provide **bilingual options (English + Chinese)** whenever requested or when the repository history shows mixed usage.
3. **Summary Line:**
   - Keep the summary concise (< 72 characters), imperative, and descriptive.
4. **Detailed Body (When appropriate):**
   - Use bullet points (`- ...`) to list key changes, non-obvious rationale, or affected components.

---

### 4. Stage and Commit

1. **Stage specific files per commit group:**
   ```bash
   git add <path/to/file1> <path/to/file2>
   ```
   *Caution*: Never stage build outputs, temporary files (e.g., `*.class`, `*.tmp`), or unreviewed sensitive files.

2. **Commit with the crafted message:**
   ```bash
   git commit -m "<message>"
   ```
   Or for multi-line messages, use multiple `-m` flags or a heredoc:
   ```bash
   git commit -m "feat(scope): summary" -m "- detail 1" -m "- detail 2"
   ```

3. **Verify state:**
   ```bash
   git status -s
   ```
   Ensure remaining files are either ready for subsequent commits or cleanly untracked if intentionally ignored.
