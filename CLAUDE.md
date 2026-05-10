# CLAUDE.md

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

Single `app` module for now, organized by package:

- `core/`: TODO
- `data/`: Room entities, DAO, repository implementations, remote API
- `domain/`: models, repository interfaces, use cases
- `feature/`: screens, ViewModels, UI state
- `di/`: Hilt modules
- `worker/`: TODO

Keep package boundaries clean so the project can be modularized later if needed.

## Database

- `user_profiles`: basic user info
- `workouts` -> `exercise_logs` -> `sets_logs`: 3-level hierarchy (1:N:N)
- `ai_provider_configs`: AI settings
- `encryptedApiKey` uses Keystore AES-GCM (standalone).
- DataStore: `active_ai_provider_id` for dynamic engine switching.
- Database version upgrade does not require migration.

## Code Style Guidelines

- Always use Javadoc-style comments for all public classes, interfaces, methods, and significant fields.
