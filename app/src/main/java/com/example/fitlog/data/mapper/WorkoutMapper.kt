package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.model.Workout
import java.time.LocalDate

fun WorkoutEntity.toModel() : Workout {
    return Workout(
        id = id,
        userId = userId,
        date = date,
        // TODO: 这里需要额外查询 ExerciseLogEntity 和 SetLogEntity 来填充
        exercises = emptyList(),
        feelings = feelings,
        sourceFileName = sourceFileName
    )
}

fun Workout.toEntity() : WorkoutEntity {
    return WorkoutEntity(
        id = id,
        userId = userId,
        date = date,
        feelings = feelings,
        sourceFileName = sourceFileName,
        // TODO: 这里可以考虑将 Workout 转换回 Markdown 格式存储到 rawContent，以便 AI 解析
        rawContent = null,
    )
}