# AGENTS.md

## Project

AI-powered native Android fitness app for personal use and portfolio/demo purposes.

## Tech Stack

- Kotlin
- Jetpack Compose
- MVVM
- Room
- Hilt
- Coroutines + Flow
- Navigation3
- Retrofit / OkHttp
- WorkManager (TODO)

## Architecture

Single `app` module, organized by package:

- `data/`: Room (entities, DAOs, converters, relation wrappers, repos), Retrofit/OkHttp, file I/O
- `data/local/relation/`: Room `@Relation` wrapper classes for multi-level eager-loading (Workout→ExerciseLog→SetLog, WorkoutPlan→PlannedSession→PlannedExercise)
- `data/file/`: Markdown file scanner and parser for importing workout logs
- `data/remote/dto/`: AI API request/response DTOs
- `model/`: domain models (no separate `domain/` package yet — repos call DAOs directly, no interfaces or use cases)
- `feature/`: screens, ViewModels, UI state (currently only `workout/`)
- `di/`: Hilt modules
- `util/`: utilities (e.g. `KeystoreManager` for encrypted API key)

Keep package boundaries clean so the project can be modularized later if needed.

## Database

- `user_profiles`: basic user info.
- `workouts` -> `exercise_logs` -> `set_logs`: 3-level workout log hierarchy (1:N:N), entities under `entity/workout/`.
- `exercises`: exercise library, business IDs in kebab-case (e.g. `barbell-bench-press`), enums and lists stored via `ExerciseConverters`.
- `workout_plans` -> `planned_sessions` -> `planned_exercises`: 3-level plan hierarchy (1:N:N), entities under `entity/plan/`.
- `ai_provider_configs`: AI provider settings.
- Multi-level queries use `@Relation` + `@Transaction`, not manual JOIN assembly.
- `encryptedApiKey` uses Keystore AES-GCM (standalone).
- DataStore: `active_ai_provider_id` for dynamic engine switching.
- Database version upgrade does not require migration.

## Code Style Guidelines

- Always use Javadoc-style comments for all public classes, interfaces, methods, and significant fields.
