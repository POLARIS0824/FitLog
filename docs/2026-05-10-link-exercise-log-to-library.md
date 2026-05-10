# 关联训练日志与标准动作库的设计分析

## 背景与问题

当前 `ExerciseLogEntity` 仅存储 `name: String`，没有关联到标准动作库 `ExerciseEntity`（kebab-case 业务 ID）。这导致：

- 无法按动作做历史统计（如查询"杠铃卧推"所有历史记录）。
- 无法复用 `ExerciseEntity` 中的肌群、动作模式、难度等元数据。
- 标准库动作改名时，历史记录断联。
- `PlannedExercise` 已经通过 `exerciseKey` 关联标准库，但 `ExerciseLog` 没有，设计不对称。

另一个项目的设计：每条训练记录的 `Exercise` 有两个 ID——自增实例 ID + 指向标准库的 `idExerciseDC`，并保留本次专属信息（notes、setMode、restTime、position）。

## 结论

**用户记录的 exercise 应该关联标准动作库。**

## 推荐修改方案

### 1. 数据库层（Entity）

在 `ExerciseLogEntity` 中增加：

- `exerciseKey: String? = null` — 外键语义指向 `ExerciseEntity.id`（kebab-case）。如果用户输入的动作名称在标准库中不存在，自动创建一条 `isCustom = true` 的 `ExerciseEntity` 并填入其 `id`，保证关联完整性。

`ExerciseLogEntity` 外键约束：

```kotlin
@Entity(
    tableName = "exercise_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseKey"],
            onDelete = ForeignKey.SET_NULL,   // 标准库删除不影响历史记录
        ),
    ],
    indices = [
        Index(value = ["workoutId"]),
        Index(value = ["exerciseKey"]),
    ],
)
```

### 2. Domain 层（Model）

`ExerciseLog` 增加：

```kotlin
data class ExerciseLog(
    val name: String,
    val exerciseKey: String?,          // ← 新增，指向 ExerciseEntity.id
    val sets: List<SetLog>,
)
```

### 3. Repository 层

`WorkoutRepositoryImpl` 修改：

- `saveSession()`：写入 `exerciseKey`。若该动作名称在 `exercises` 表中不存在，则自动插入一条 `isCustom = true` 的 `ExerciseEntity`，再使用其 `id`。
- `toDomain()`：按 `exerciseKey` 查询 `ExerciseEntity` 补全最新名称与元数据。

### 4. Database 配置

`AppDatabase`：

- 版本号从 1 升级到 2。
- `exportSchema = false`，Room 会自动处理新增 nullable 列（无需 Migration）。

## 关键文件清单

| 文件 | 修改内容 |
|------|----------|
| `app/src/main/java/com/example/fitlog/data/local/entity/workout/ExerciseLogEntity.kt` | 新增 `exerciseKey` 字段及外键约束 |
| `app/src/main/java/com/example/fitlog/domain/model/WorkOut.kt` | `ExerciseLog` 新增 `exerciseKey` |
| `app/src/main/java/com/example/fitlog/data/repository/WorkoutRepositoryImpl.kt` | 保存/查询时处理 `exerciseKey`，缺失动作自动创建自定义 `ExerciseEntity` |
| `app/src/main/java/com/example/fitlog/data/local/AppDatabase.kt` | 升级 version = 2，加入 `ExerciseLogEntity` 变更 |

## 验证方式

1. 编译通过，无 Room schema 错误。
2. 运行应用，从 Markdown 导入或手动录入训练后，`exercise_logs` 表的 `exerciseKey` 列应能正确填入对应 `exercises.id`。
3. 查询某动作历史时，可通过 `exerciseKey` JOIN `exercises` 获取完整元数据。

## 已确认决策

- **不加本次专属字段**：本阶段仅引入 `exerciseKey`，不添加 `notes`/`restSeconds`，保持最小化变更。
- **缺失动作自动入库**：用户输入的动作名称若不在标准库中，`WorkoutRepositoryImpl` 自动创建一条 `isCustom = true` 的 `ExerciseEntity`，保证 `exerciseKey` 始终非空。
