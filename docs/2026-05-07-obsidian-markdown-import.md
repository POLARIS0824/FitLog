# Obsidian Markdown 健身记录导入方案

## Context

用户平时在 Obsidian（手机本地）用 markdown 记录健身训练，每天一个文件，文件名包含日期。App 作为单向解析展示工具，通过大模型 API 解析 markdown 内容后存入 Room 数据库展示。

## 现有数据结构

当前 `DailyCheckInEntity` 将整条记录序列化为 `content: String` 存储，不利于查询和统计。

## 推荐数据库重构方案

### Entity 设计

#### WorkoutEntity

```kotlin
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                    // ISO-8601: 2026-05-07
    val sourceFileName: String?,         // 来源文件名，如 "2026-05-07.md"
    val rawContent: String,              // 原始 file 全文
)
```

#### ExerciseLogEntity

```kotlin
@Entity(
    tableName = "exercise_logs",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
)
```

#### SetLogEntity

```kotlin
@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(
        entity = ExerciseLogEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseLogId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseLogId: Long,
    val setNumber: Int,
    val weightKg: Float,
    val reps: Int,
)
```

### 设计决策

- `date` 不做唯一键，支持同一天多练（Obsidian 每天一个文件，但后续 App 直接记录可能导致同一天多条）
- 主键使用 Room `autoGenerate = true`，标准做法
- 保留 `rawContent` 方便 AI 解析出错时对照排查
- CASCADE 级联删除
- 与现有 domain model `DailyCheckIn` / `ExerciseEntry` / `WorkoutSet` 对齐

## 后续工作（本计划范围外）

1. 创建对应 DAO 接口
2. 更新 `AppDatabase`（version 1 → 2）并编写 Migration
3. 实现 `WorkoutRepositoryImpl`
4. 构建大模型解析流程（prompt → API → JSON → Entity）
5. 添加文件扫描逻辑读取 Obsidian vault
6. UI 层展示训练日志
