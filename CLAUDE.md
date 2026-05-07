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
- Navigation Compose
- Retrofit / OkHttp
- WorkManager

## Architecture

Single `app` module for now, organized by package:

- `core/`: theme, navigation, shared utilities
- `data/`: Room entities, DAO, repository implementations, remote API
- `domain/`: models, repository interfaces, use cases
- `feature/`: screens, ViewModels, UI state
- `ai/`: analyzers, prompt builders, AI formatting
- `di/`: Hilt modules
- `worker/`: background jobs like weekly summaries

Keep package boundaries clean so the project can be modularized later if needed.

## Code Style Guidelines

- Always use Javadoc-style comments for all public classes, interfaces, methods, and significant fields.
