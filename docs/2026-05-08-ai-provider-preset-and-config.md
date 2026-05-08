# Plan: AI Provider Preset & Config Persistence

## Context

用户已定义 `AIProviderPreset`（硬编码服务商列表）和 `AIProviderConfig`（用户自定义配置含 API Key）。
需要：
1. 在 `data` 层实现硬编码的 preset 列表（OpenAI、Anthropic、DeepSeek）。
2. 用 Room 持久化用户保存的 `AIProviderConfig`。
3. 遵循现有架构（Entity → DAO → RepositoryImpl → Domain Interface → Hilt Module）。

## Decision: Room over DataStore

- `AIProviderConfig` 是 6 字段结构化数据，且未来可能保存多条配置（多服务商切换）。
- 项目已有完整的 Room 基础设施（AppDatabase、DAO、Migration、Hilt），无 DataStore 依赖。
- DataStore 更适合 key-value 偏好设置，不适合此场景。

## Implementation

### 1. New Files

| Path | Purpose |
|------|---------|
| `data/local/entity/AIProviderConfigEntity.kt` | Room entity for `ai_provider_configs` table |
| `data/local/dao/AIProviderConfigDao.kt` | Standard Room DAO (`@Insert`, `@Update`, `@Delete`, `@Query`) |
| `domain/repository/AIProviderConfigRepository.kt` | Domain interface for config CRUD |
| `domain/repository/AIProviderPresetRepository.kt` | Domain interface for preset read-only access |
| `data/repository/AIProviderConfigRepositoryImpl.kt` | Room-backed impl with private extension mapping |
| `data/repository/AIProviderPresetRepositoryImpl.kt` | Hardcoded preset list impl |
| `data/repository/AIProviderConfigRepositoryImplTest.kt` | Unit test with Fake DAO (mirrors existing test patterns) |

### 2. Modified Files

| Path | Change |
|------|--------|
| `data/local/AppDatabase.kt` | Add `AIProviderConfigEntity::class` to `@Database`, bump version `2 → 3`, add `aiProviderConfigDao()` accessor, add `MIGRATION_2_3` |
| `di/DatabaseModule.kt` | Add `provideAIProviderConfigDao()`, add `@Binds` for both new repositories, wire `MIGRATION_2_3` into Room builder |

### 3. Reused Patterns

- **Repository impl pattern**: Copy from `UserProfileRepositoryImpl.kt` — constructor-injected DAO, private `toDomain()` / `toEntity()` extensions.
- **DAO pattern**: Copy from `UserProfileDao.kt` — `@Insert(onConflict = OnConflictStrategy.IGNORE)`, `@Update`, `@Delete`, `@Query`.
- **Fake DAO testing**: Copy from `UserProfileRepositoryImplTest.kt` — in-memory `MutableList` backing.
- **Javadoc**: All public classes/interfaces/methods per `CLAUDE.md`.

### 4. Migration (`MIGRATION_2_3`)

```sql
CREATE TABLE ai_provider_configs (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    baseUrl TEXT NOT NULL,
    apiKey TEXT NOT NULL,
    model TEXT NOT NULL,
    isPreset INTEGER NOT NULL DEFAULT 0
)
```

No data migration needed (new empty table). `isPreset` maps to SQLite `INTEGER`.

### 5. Preset Exposure

Use repository interface + injectable impl for consistency and testability, even though data is hardcoded. This keeps call sites unchanged if presets ever move to remote config.

### 6. Verification

1. Run `./gradlew :app:kspDebugKotlin` to verify Room schema compiles.
2. Run `./gradlew :app:testDebugUnitTest` to verify new repository tests pass.
3. Verify app launches without migration crash (database version 2 → 3).
