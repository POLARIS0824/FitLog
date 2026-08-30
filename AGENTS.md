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
- `data/local/relation/`: `@Relation` wrappers for 3-level eager-loading (`WorkoutWithExerciseLogs`, `WorkoutPlanWithSessions`)
- `data/file/`: `MarkdownFileScanner` and `MarkdownParser` for importing workout logs
- `model/`: Domain models (repos map DAOs/DTOs directly to domain models)
- `feature/`: Feature modules (`agent` AI engine + tools, `aisettings`, `chat`, `stats`, `today`, `workout`)
- `ui/`: Global UI components, Theme, Navigation3 routes; settings subpages (`appearance`, `dataimport`, `profile`, `reminder`, `SettingsScreen`, `AboutScreen`)
- `di/`: Hilt modules (`DatabaseModule`, `AIModule`, `AgentEngineModule`)
- `util/`: Utilities (e.g. `KeystoreManager` for AES-GCM API key encryption, `VolumeFormatter`/`VolumeAggregator` for shared workout-metric conventions)

Keep package boundaries clean for potential future modularization.

## UI & Design System

- Follow Google Material Expressive design system.
- Screen Use xxxRoute & xxxScreen pattern.
- Settings-style pages share the collapsing dual-title behavior via `ui/components/CollapsingTitleScaffold`.

## Database

- `user_profiles`: User profile info.
- `workouts` -> `exercise_logs` -> `set_logs`: 3-level workout log hierarchy (1:N:N) in `entity/workout/`.
- `workout_plans` -> `planned_sessions`: plan hierarchy (1:N) in `entity/plan/`; the former `planned_exercises` table was dropped — each session embeds its exercise list as a JSON column.
- `exercises`: Exercise library (kebab-case IDs e.g. `barbell-bench-press`).
- `ai_provider_configs`: AI provider settings (AES-GCM encrypted API key).
- DataStore: `active_ai_provider_id` for dynamic engine switching.
- Multi-level queries use `@Relation` + `@Transaction`.
- Schema changes MUST bump `AppDatabase.version`, add a `Migration` in `data/local/Migrations.kt`, and commit the exported schema JSON (`app/schemas/`). No destructive migrations.

## Code Style & Guidelines

- Always use Javadoc-style comments for all public classes, interfaces, methods, and significant fields.
- Form state lives in ViewModel (`MutableStateFlow` + `combine`), UI holds transient state (sheets/dropdowns).
- Never run `./gradlew` directly inside WSL
