---
name: git-commit
description: Inspect uncommitted working tree changes, group or split files into logical atomic commits, and propose single-line Conventional Commit messages and single-line git add commands without auto-committing.
---

# Git Commit

Inspects unstaged and untracked working tree changes, plans atomic commits, and generates concise Conventional Commit messages and commands aligned with repository history.

## Core Directives

1. **Do NOT automatically commit by default**: Propose the commit message and commands for the user to review. Never run `git commit` or `git add` unless the user explicitly requests direct execution.
2. **Single-line `git add` commands**: Always output `git add` as a single, contiguous line without backslashes (`\`) or line continuations (e.g., `git add path/to/file1 path/to/file2`).
3. **One-liner commit messages (single `-m`)**: Default to a concise one-line Conventional Commit message with a single `-m` flag (`git commit -m "<type>(<scope>): <summary>"`). Do not generate multi-line bullet lists or multiple `-m` flags unless the user explicitly asks for a detailed commit body.

## Workflow

### 1. Inspect Status and History

Run the following commands before formulating commit proposals:

1. **Check uncommitted files:**
   ```bash
   git status -s
   ```
   Identify all modified (`M`), added (`A`), deleted (`D`), and untracked (`??`) files.

2. **Inspect recent repository style (last 3 commits):**
   ```bash
   git log -n 3 --oneline
   ```
   Extract scope conventions, language (English/Chinese/bilingual), and recent formatting patterns.

3. **Inspect the actual diffs:**
   ```bash
   git diff
   ```
   Review the intent and semantic scope of the changes.

---

### 2. Group & Split Commits (Atomic Commits)

Evaluate whether changes should be split into multiple atomic commits:

- **Split when:**
  - Changes span unrelated features, bug fixes, or architecture layers.
  - Documentation, configuration, or dependency updates are mixed with business logic.
  - Refactoring was performed alongside new user-facing functionality.
- **Group together when:**
  - All modifications serve a single cohesive intent (e.g., a feature implementation together with its unit tests).
  - Splitting would leave the repository in a broken or non-compiling intermediate state.

---

### 3. Craft Conventional Commit Messages

Follow the Conventional Commits specification:
```text
<type>(<scope>): <summary>
```

#### Format Rules:
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
2. **Summary format (One-liner by default):**
   - Keep the summary concise (< 72 characters), imperative, and descriptive.
   - Use a single `-m` flag (`git commit -m "..."`). Do not append multi-line bullet points or secondary `-m` flags by default.
3. **Language & Bilinguality:**
   - Respect repository language style from recent commits.
   - Provide bilingual options (English + Chinese) when requested or when repository history shows mixed usage.

---

### 4. Output Proposal to the User

Present the proposal clearly without running `git commit`:

1. **Summary of changes & grouping rationale.**
2. **Proposed single-line `git add` command(s)** (no `\` line continuations):
   ```bash
   git add path/to/file1 path/to/file2 path/to/file3
   ```
3. **Proposed one-liner `git commit` command(s)**:
   ```bash
   git commit -m "<type>(<scope>): <summary>"
   ```
