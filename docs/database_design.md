# FitLog 数据库设计文档

> Room 数据库版本：v6 | 数据库名：`fitlog.db` | exportSchema：false

## 1. ER 关系总览

```
user_profiles (独立，单用户)

workouts ─1:N─→ exercise_logs ─1:N─→ set_logs
   │                  │
   │                  └── FK → exercises.id (SET_NULL)
   │
   └── FK ← planned_sessions.completedWorkoutId (SET_NULL)

workout_plans ─1:N─→ planned_sessions
                        └── exercises: JSON 列 (List<PlannedExerciseItem>)

exercises (独立，动作库)

ai_provider_configs (独立，AI 配置)

body_metrics (独立，按天去重)
```

## 2. 表结构详解

### 2.1 workouts（训练日）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PK, autoGenerate | 自增主键 |
| userId | INTEGER | NOT NULL, default 0 | 用户 ID（单用户固定 0） |
| date | TEXT (LocalDate) | NOT NULL, INDEX | 训练日期 |
| feelings | TEXT | nullable | 训练感受/备注 |
| startedAt | INTEGER | nullable | 开始时间 epoch millis |
| endedAt | INTEGER | nullable | 结束时间 epoch millis |
| sourceFileName | TEXT | nullable | 导入来源文件名 |
| rawContent | TEXT | nullable | 原始 Markdown 全文 |

**索引**：`INDEX(date)` — 按日期查询和排序的主要路径。

**设计要点**：
- `sourceFileName` + `rawContent` 服务于 Markdown 导入去重和 AI 解析排查
- `startedAt`/`endedAt` 差值即训练时长，可选（导入数据无时间戳）

### 2.2 exercise_logs（动作记录）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PK, autoGenerate | 自增主键 |
| workoutId | INTEGER | FK → workouts.id (CASCADE), INDEX | 所属训练日 |
| exerciseKey | TEXT | FK → exercises.id (SET_NULL), INDEX, nullable | 动作库关联 |
| name | TEXT | NOT NULL | 动作名称（冗余，降级显示） |
| sortOrder | INTEGER | NOT NULL | 当天排序序号 |

**外键策略**：
- `workoutId` CASCADE：删除训练日时连带删除所有动作记录
- `exerciseKey` SET_NULL：动作库条目被删时，日志保留名称但断开关联

**设计要点**：
- `name` 冗余存储是刻意的降级策略：导入数据可能无法匹配动作库
- `exerciseKey` 可空：自由训练/导入数据可能无标准动作 ID

### 2.3 set_logs（组记录）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PK, autoGenerate | 自增主键 |
| exerciseLogId | INTEGER | FK → exercise_logs.id (CASCADE), INDEX | 所属动作 |
| setNumber | INTEGER | NOT NULL | 组号（1-based） |
| weightKg | REAL | NOT NULL | 重量（kg） |
| reps | INTEGER | NOT NULL | 次数 |
| setType | TEXT | NOT NULL, default "WORKING" | 组类型枚举名 |

**设计要点**：
- `setType` 存枚举名字符串（"WARMUP"/"WORKING"），容量统计只累加 WORKING
- Mapper 层有容错：非法枚举值按 WORKING 处理

### 2.4 exercises（动作库）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | TEXT | PK | kebab-case 业务标识 |
| name | TEXT | NOT NULL | 动作名称 |
| primaryMuscles | TEXT | NOT NULL | 逗号分隔枚举名 |
| secondaryMuscles | TEXT | NOT NULL | 逗号分隔枚举名 |
| isCompound | INTEGER | NOT NULL | 是否复合动作 |
| isCustom | INTEGER | NOT NULL | 是否用户自定义 |
| equipment | TEXT | nullable | 器械枚举名 |
| bodyPart | TEXT | NOT NULL | 身体部位枚举名 |
| description | TEXT | nullable | 动作说明 |
| instructions | TEXT | NOT NULL | JSON 字符串列表 |
| imageUrl | TEXT | nullable | 缩略图路径 |
| gifUrl | TEXT | nullable | GIF URL |

**设计要点**：
- 主键为语义 ID（如 `barbell-bench-press`），非自增——便于 JSON 导入导出和 AI 上下文
- `primaryMuscles` 用逗号分隔存储（TypeConverter），查询用 LIKE 模糊匹配
- `instructions` 用 JSON 序列化（TypeConverter），容错降级为空格分割

### 2.5 workout_plans（训练计划）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | TEXT | PK | 业务标识 |
| name | TEXT | NOT NULL | 计划名称 |
| description | TEXT | nullable | 计划说明 |
| goal | TEXT | nullable | 训练目标枚举名 |
| durationWeeks | INTEGER | NOT NULL | 持续周数 |
| sessionsPerWeek | INTEGER | NOT NULL | 每周训练次数 |
| isCustom | INTEGER | NOT NULL | 是否自定义 |
| createdAt | TEXT (LocalDate) | NOT NULL | 创建日期 |
| rawPlanText | TEXT | nullable | AI 生成原文 |

### 2.6 planned_sessions（计划训练日）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | TEXT | PK | 业务标识 |
| planId | TEXT | FK → workout_plans.id (CASCADE), INDEX | 所属计划 |
| name | TEXT | NOT NULL | 训练日名称 |
| description | TEXT | nullable | 说明 |
| dayNumber | INTEGER | NOT NULL | 第几天（1-based） |
| weekNumber | INTEGER | NOT NULL | 第几周（1-based） |
| targetDurationMinutes | INTEGER | nullable | 目标时长 |
| exercises | TEXT | NOT NULL | JSON: List\<PlannedExerciseItem\> |
| completedWorkoutId | INTEGER | FK → workouts.id (SET_NULL), INDEX, nullable | 完成关联 |

**设计要点**：
- 动作清单内嵌 JSON 列，不设独立 `planned_exercises` 表——动作从不跨 session 独立查询
- `completedWorkoutId` 实现"计划 ↔ 实际训练"的双向关联
- 排序查询：`ORDER BY weekNumber, dayNumber`

### 2.7 ai_provider_configs（AI 配置）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | TEXT | PK | 配置标识（如 "DEEPSEEK"） |
| name | TEXT | NOT NULL | 展示名称 |
| type | TEXT | NOT NULL, default "CUSTOM" | 平台类型枚举名 |
| baseUrl | TEXT | NOT NULL | API 基础地址 |
| encryptedApiKey | TEXT | NOT NULL | AES-GCM 密文 |
| model | TEXT | NOT NULL | 默认模型 |
| customEndpoint | TEXT | nullable | 自定义路径 |
| apiVersion | TEXT | nullable | API 版本（Azure） |
| isPreset | INTEGER | NOT NULL | 是否内置预设 |
| cachedModels | TEXT | nullable | 逗号分隔模型列表 |

### 2.8 user_profiles（用户资料）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PK, autoGenerate | 自增主键 |
| name | TEXT | NOT NULL | 姓名 |
| age | INTEGER | nullable | 年龄 |
| gender | TEXT | nullable | 性别枚举名 |
| height | REAL | nullable | 身高 cm |
| weight | REAL | nullable | 体重 kg |
| trainingGoal | TEXT | nullable | 训练目标枚举名 |

### 2.9 body_metrics（身体指标）

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| date | TEXT (LocalDate) | PK | 记录日期（业务主键） |
| weightKg | REAL | NOT NULL | 体重 kg |

**设计要点**：以 `date` 为主键 + `REPLACE` 插入 = 按天去重 upsert，无需唯一索引。

## 3. TypeConverter 策略

| Converter 类 | 转换内容 |
|--------------|----------|
| `ExerciseConverters` | List\<Muscle\> ↔ 逗号字符串；BodyPart/Equipment ↔ 枚举名；List\<String\> ↔ JSON |
| `LocalDateConverters` | LocalDate ↔ ISO-8601 字符串 |
| `PlanConverters` | List\<PlannedExerciseItem\> ↔ JSON |

## 4. 级联查询（@Relation）

### WorkoutWithExerciseLogs（3 级）

```
WorkoutEntity
  └── List<ExerciseLogWithSets>
        └── List<SetLogEntity>
```

### WorkoutPlanWithSessions（2 级）

```
WorkoutPlanEntity
  └── List<PlannedSessionEntity>  (exercises 已在 JSON 列内)
```

所有级联查询均标注 `@Transaction`，保证读取一致性。

## 5. 数据流：写入路径

### 训练日志插入（事务级联）

```
WorkoutRepository.insert(workout)
  └── db.withTransaction {
        workoutDao.insert(entity) → workoutId
        exerciseLogDao.insert(log) → exerciseLogId  // × N
        setLogDao.insertAll(sets)                    // × M
      }
```

### 训练日志更新（删旧插新）

```
WorkoutRepository.update(workout)
  └── db.withTransaction {
        workoutDao.update(entity)
        exerciseLogDao.deleteByWorkoutId(id)  // CASCADE 连带删 set_logs
        insertChildren(id, workout)           // 重新插入
      }
```

## 6. 问题与改进建议

| # | 问题 | 严重度 | 建议 |
|---|------|--------|------|
| 1 | `exportSchema = false`，无迁移历史 | 中 | 开启 `exportSchema = true`，配置 `room.schemaLocation` |
| 2 | `fallbackToDestructiveMigration()`，升级丢数据 | 高 | 发布前必须编写 Migration 或至少 `fallbackToDestructiveMigrationOnDowngrade` |
| 3 | `ExerciseDao.getByMuscle()` 用 LIKE 匹配逗号字符串 | 低 | 数据量小可接受；若需精确匹配考虑关联表 |
| 4 | `ExerciseConverters.toMuscleList()` 无容错 | 中 | 添加 `runCatching` 或 `filterNotNull`，防未知枚举值崩溃 |
| 5 | `ExerciseConverters.toBodyPart()` 无容错 | 中 | 同上，`BodyPart.valueOf()` 对非法值直接抛异常 |
| 6 | `workouts` 表无分页查询 | 低 | 数据量增长后添加 `LIMIT/OFFSET` 或 Paging3 |
| 7 | `userId` 字段冗余（单用户固定 0） | 低 | 可保留为未来多用户预留，当前无实际作用 |
| 8 | `ai_provider_configs.cachedModels` 逗号分隔 | 低 | 模型名含逗号时会出错；概率极低，可接受 |
| 9 | 无 `planned_exercises` 独立表 | — | 刻意设计，动作清单从不独立查询，JSON 列合理 |
| 10 | `WorkoutPlanDao` 有重复查询方法 | 低 | `getAllPlansWithDetails()` 与 `getAllPlansWithDetailsFlow()` 可合并 |
