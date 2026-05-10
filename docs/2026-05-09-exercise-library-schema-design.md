# 标准动作库（Exercise Library）数据库设计

## Context

当前训练日志中的动作为自由文本（`ExerciseLogEntity.name: String`），AI 无法结构化地理解用户练了哪些肌肉、用了什么器械。为了让后续智能健身 Agent 能进行肌肉平衡分析、动作替代推荐、训练计划生成，需要建立一张标准动作库表，并关联肌肉群信息。

## 方案概览

新增 **3 张表** + **修改 1 张表** + **新增 1 组枚举**。

### 新增表 1：exercises（标准动作库）

存储标准化动作的基本信息。

```kotlin
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,               // 如 "杠铃卧推"
    val category: String,           // ExerciseCategory.name: "CHEST"
    val equipment: String,          // EquipmentType.name: "BARBELL"
    val movementType: String,       // MovementType.name: "PUSH"
    val difficulty: String?,        // DifficultyLevel.name: "INTERMEDIATE"
    val description: String?,       // 动作描述
    val instructions: String?,      // 执行要点
)
```

### 新增表 2：muscle_groups（肌肉群字典）

中等粒度：胸大肌、背阔肌、三角肌、肱二头肌、肱三头肌、股四头肌、腘绳肌、臀大肌、竖脊肌、腹直肌等。

```kotlin
@Entity(tableName = "muscle_groups")
data class MuscleGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,               // 如 "胸大肌"
    val bodyPart: String,           // BodyPart.name: "CHEST"
    val isFront: Boolean,           // 正面肌群 true
    val isMajor: Boolean,           // 大肌群 true
)
```

### 新增表 3：exercise_muscles（多对多关联）

记录每个动作练到的肌肉及角色（主要/次要/稳定）。

```kotlin
@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exerciseId", "muscleId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["muscleId"])],
)
data class ExerciseMuscleEntity(
    val exerciseId: Long,
    val muscleId: Long,
    val role: String,               // MuscleRole.name: "PRIMARY" / "SECONDARY" / "STABILIZER"
)
```

### 修改表：exercise_logs

在现有 `ExerciseLogEntity` 上增加可空的 `exerciseId` 外键，保留 `name` 字符串作为回退。

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
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workoutId"]),
        Index(value = ["exerciseId"]),
    ],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workoutId: Long,
    val exerciseId: Long?,          // 新增：关联标准动作库（可空）
    val name: String,               // 保留：原始/自定义名称
    val sortOrder: Int,
)
```

### 新增 Domain 枚举

位置：`domain/model/Exercise.kt`（或单独文件）

```kotlin
enum class ExerciseCategory { CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, FULL_BODY }
enum class EquipmentType { BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, KETTLEBELL, OTHER }
enum class MovementType { PUSH, PULL, SQUAT, HINGE, EXTENSION, FLEXION, ROTATION, ISOMETRIC }
enum class MuscleRole { PRIMARY, SECONDARY, STABILIZER }
enum class BodyPart { CHEST, SHOULDERS, BACK, ARMS, LEGS, CORE }
enum class DifficultyLevel { BEGINNER, INTERMEDIATE, ADVANCED }
```

### 新增 Domain Model

```kotlin
data class Exercise(
    val id: Long,
    val name: String,
    val category: ExerciseCategory,
    val equipment: EquipmentType,
    val movementType: MovementType,
    val difficulty: DifficultyLevel?,
    val description: String?,
    val instructions: String?,
    val targetMuscles: List<ExerciseMuscleTarget>,
)

data class ExerciseMuscleTarget(
    val muscle: MuscleGroup,
    val role: MuscleRole,
)

data class MuscleGroup(
    val id: Long,
    val name: String,
    val bodyPart: BodyPart,
    val isFront: Boolean,
    val isMajor: Boolean,
)
```

## 关键文件清单

### 新建

| 文件路径 | 说明 |
|----------|------|
| `data/local/entity/ExerciseEntity.kt` | 覆盖现有空文件 |
| `data/local/entity/MuscleGroupEntity.kt` | 肌肉群实体 |
| `data/local/entity/ExerciseMuscleEntity.kt` | 关联实体 |
| `data/local/dao/ExerciseDao.kt` | 动作库 CRUD |
| `data/local/dao/MuscleGroupDao.kt` | 肌肉群查询 |
| `data/local/dao/ExerciseMuscleDao.kt` | 关联查询 |
| `domain/model/Exercise.kt` | Domain 模型与枚举 |
| `domain/repository/ExerciseRepository.kt` | 仓库接口 |
| `data/repository/ExerciseRepositoryImpl.kt` | 仓库实现 |
| `di/ExerciseModule.kt` | Hilt 绑定模块 |

### 修改

| 文件路径 | 修改内容 |
|----------|----------|
| `data/local/entity/ExerciseLogEntity.kt` | 增加 `exerciseId` 外键及索引 |
| `data/local/AppDatabase.kt` | 注册 3 个新实体、增加 `MIGRATION_4_5`、新增 DAO 抽象方法 |
| `data/repository/WorkoutRepositoryImpl.kt` | `saveSession` / `toDomain` 处理 `exerciseId` |
| `domain/model/DailyCheckIn.kt` | `ExerciseEntry` 增加可选的 `exerciseId` |

## 种子数据策略

在 `AppDatabase` 的 `RoomDatabase.Callback.onCreate` 中预置初始数据：

- **muscle_groups**：约 15-20 条（胸大肌、背阔肌、三角肌前束/中束/后束、肱二头肌、肱三头肌、股四头肌、腘绳肌、臀大肌、竖脊肌、腹直肌、斜方肌、菱形肌等）
- **exercises**：约 30-40 条常见动作（杠铃卧推、哑铃飞鸟、高位下拉、杠铃划船、深蹲、硬拉、肩推、二头弯举等）
- **exercise_muscles**：每个动作关联 2-5 条肌肉记录，标记 PRIMARY/SECONDARY

## 验证方式

1. **编译**：`./gradlew :app:compileDebugKotlin` 无错误。
2. **迁移**：卸载重装或升级应用，Database Inspector 确认 `exercises`、`muscle_groups`、`exercise_muscles` 表存在，且 `exercise_logs` 新增 `exerciseId` 列。
3. **种子数据**：启动应用后，Database Inspector 中确认预设动作和肌肉数据已插入。
4. **集成**：保存一条训练记录时，`exercise_logs.exerciseId` 正确写入；读取时 `DailyCheckIn.ExerciseEntry.exerciseId` 正确还原。
