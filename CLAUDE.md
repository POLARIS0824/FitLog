# CLAUDE.md

## Project

AI-powered native Android fitness app for personal use and portfolio/demo purposes.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3 / Expressive)
- MVVM
- Room
- Hilt
- Coroutines + Flow
- Navigation3
- Retrofit / OkHttp
- WorkManager (TODO)

## Architecture & Package Structure

Single `app` module, organized by package:

- `data/`: Room (entities, DAOs, converters, relation wrappers), Retrofit/OkHttp DTOs & API, Repositories, SAF file I/O
- `data/agent/`: Agent 核心（`AgentOrchestrator` tool-calling 循环、`AgentToolRegistry`、`AgentPromptBuilder`、`ChatCompletionClient` 端口）；`data/agent/tools/` 为只读 `AgentTool` 实现
- `data/local/relation/`: `@Relation` wrappers for 3-level eager-loading (`WorkoutWithExerciseLogs`, `WorkoutPlanWithSessions`)
- `data/file/`: `MarkdownFileScanner` and `MarkdownParser` for importing workout logs
- `model/`: Domain models (repos map DAOs/DTOs directly to domain models); `model/ai/` 含 `AgentTool` 接口、`ToolCall`、`ToolDefinition`
- `feature/`: Feature modules (`aisettings`, `chat`——tool-calling AI 教练，App 根页面, `workout`)
- `ui/`: Global UI components, Theme, Navigation3 routes (`appearance`, `dataimport`, `profile`, `reminder`, `SettingsScreen`)
- `di/`: Hilt modules (`DatabaseModule`, `AIModule`)
- `util/`: Utilities (e.g. `KeystoreManager` for AES-GCM API key encryption)

Keep package boundaries clean for potential future modularization.

## UI & Design System

- Follow Google Material Expressive design system.
- Screen Use xxxRoute & xxxScreen pattern.

## Database

- `user_profiles`: User profile info.
- `workouts` -> `exercise_logs` -> `set_logs`: 3-level workout log hierarchy (1:N:N) in `entity/workout/`.
- `workout_plans` -> `planned_sessions` -> `planned_exercises`: 3-level plan hierarchy (1:N:N) in `entity/plan/`.
- `exercises`: Exercise library (kebab-case IDs e.g. `barbell-bench-press`).
- `ai_provider_configs`: AI provider settings (AES-GCM encrypted API key).
- DataStore: `active_ai_provider_id` for dynamic engine switching.
- Multi-level queries use `@Relation` + `@Transaction`.

## Code Style & Guidelines

- Always use Javadoc-style comments for all public classes, interfaces, methods, and significant fields.
- Form state lives in ViewModel (`MutableStateFlow` + `combine`), UI holds transient state (sheets/dropdowns).
