package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.ExerciseLogWithSets
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout

fun WorkoutEntity.toModel(): Workout {
    return Workout(
        id = id,
        userId = userId,
        date = date,
        exercises = emptyList(),
        feelings = feelings,
        startedAt = startedAt,
        endedAt = endedAt,
        sourceFileName = sourceFileName,
        rawContent = rawContent,
    )
}

fun WorkoutWithExerciseLogs.toModel(): Workout {
    return Workout(
        id = workout.id,
        userId = workout.userId,
        date = workout.date,
        exercises = exerciseLogs.map { it.toModel() },
        feelings = workout.feelings,
        startedAt = workout.startedAt,
        endedAt = workout.endedAt,
        sourceFileName = workout.sourceFileName,
        rawContent = workout.rawContent,
    )
}

fun ExerciseLogWithSets.toModel(): ExerciseLog {
    return ExerciseLog(
        name = exerciseLog.name,
        exerciseKey = exerciseLog.exerciseKey,
        sets = sets.map {
            SetLog(
                weightKg = it.weightKg,
                reps = it.reps,
                // 容错：库中非法枚举字符串按正式组处理（同 ThemeMode 容错风格）
                setType = runCatching { SetType.valueOf(it.setType) }
                    .getOrDefault(SetType.WORKING),
            )
        },
    )
}

fun Workout.toEntity(): WorkoutEntity {
    return WorkoutEntity(
        id = id,
        userId = userId,
        date = date,
        feelings = feelings,
        startedAt = startedAt,
        endedAt = endedAt,
        sourceFileName = sourceFileName,
        rawContent = rawContent,
    )
}

/**
 * 领域模型 [ExerciseLog] → 数据库实体。
 *
 * @param workoutId 所属训练日的数据库主键（级联插入时由 workout 插入返回值提供）
 * @param sortOrder 动作在当天的排序序号
 */
fun ExerciseLog.toEntity(workoutId: Long, sortOrder: Int): ExerciseLogEntity {
    return ExerciseLogEntity(
        workoutId = workoutId,
        exerciseKey = exerciseKey,
        name = name,
        sortOrder = sortOrder,
    )
}

/**
 * 领域模型 [SetLog] → 数据库实体。
 *
 * @param exerciseLogId 所属动作记录的数据库主键（级联插入时由 exerciseLog 插入返回值提供）
 * @param setNumber 组号（1-based）
 */
fun SetLog.toEntity(exerciseLogId: Long, setNumber: Int): SetLogEntity {
    return SetLogEntity(
        exerciseLogId = exerciseLogId,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        setType = setType.name,
    )
}